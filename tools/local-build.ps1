<#
.SYNOPSIS
  Local build of the Agiri APKs in about 30 seconds, using the rootfs and proot taken from a CI release. Windows PowerShell 5.1 only.

.DESCRIPTION
  CI needs about 10 minutes per push because it builds proot and the Debian rootfs. Neither changes between most
  pushes, so a local build reuses them: the parts come out of an APK of a published phase0-N release.

  Run it (script execution is disabled on this PC, so always go through -ExecutionPolicy Bypass):
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1 [switches]

  Three builds, one identity each (package names come from tools\build-ids.ps1):
    (none)        the TEST TWIN: debug type, package dev.pocketagent.app.debug, label "Agiri Test", marked icons.
                  app\build\outputs\apk\debug\app-debug.apk
    -OwnerDebug   the owner's package as a debug build (no twin suffix, no twin overlays). Used until the twin can be
                  installed. Same output path as the twin: the package of the file is checked before every install.
    -Release      the OWNER build: package dev.pocketagent.app, R8 and resource shrinking, not debuggable.
                  app\build\outputs\apk\release\app-release.apk

  Switches
    -FetchParts [phase0-N]   download that release (default: the latest phase0-* release) with gh, verify the APK
                             against the release's SHA256SUMS.txt, extract rootfs + proot into dist\runtime-parts\,
                             check them against the .info files and record the tag in dist\runtime-parts\source.txt.
                             Builds afterwards unless -NoBuild is given. (The tag is a plain second word, or -Tag.)
                             Takes the twin APK of the release (Agiri-N-test.apk), or the legacy PocketAgent-phase0-N APK.
    -FromApk <file>          like -FetchParts but from a local APK (needs SHA256SUMS.txt next to it, or -Sums).
    -Release | -OwnerDebug   which build to make and install (see above); they exclude each other.
    -BuildNumber <n>         the CI run number the build belongs to (version 0.2.<n>). Default: the number of the release
                             the parts came from. Refused when lower than that number.
    -Install                 adb install -r the built APK on the one connected phone (-Serial <id> to pick one).
    -Start                   -Install, then launch the app.
    -NoCompile               skip the compile step (cmd package compile -m speed -f) that follows -Release -Install.
    -CheckSigning            print the certificate digest of the built APK and of the installed app, then exit:
                             0 same, 3 different or not installed.
    -AcknowledgeS2           required for -Release -Install: says that experiment S2 (the optimised build) passed on the twin.
    -AllowReextract          the owner's install normally refuses an APK whose Linux image differs from the installed one
                             (it would extract, park and restore the agent's data). This switch lifts that one refusal.
    -Test                    run :app:testDebugUnitTest from an apostrophe-free copy of the tree (see the note below);
                             with -OwnerDebug the owner-debug variant is tested.
    -NoBuild                 skip the build (e.g. -FetchParts -NoBuild, or -Install of the APK built last time).
    -ListDevices             only list the phones adb sees (starts the adb server if needed); installs nothing.

  The only positional parameter is the tag: "-FetchParts phase0-8". Any other stray word is an error.

  Installing on the OWNER's package (-Release or -OwnerDebug with -Install) goes only through Assert-OwnerInstallAllowed:
  same signing certificate as the installed app, a higher version code, a read-only copy of the owner's data in
  dist\owner-backup\ that is newer than the installed app, and the same Linux image. The script never removes an app,
  never clears data, never changes a system setting and never allows a downgrade of the owner's package.

  Output lines for humans and the robot: APK:, PACKAGE:, VERSION:, CERT-SHA256:, INSTALLED-CERT-SHA256:, COMPILE:.
  Exit codes: 0 ok, 1 error, 2 only the expected Windows symlink test failures, 3 refused for safety.

  A plain run stops with a clear message when Java or the Android SDK pieces are missing, when the parts are missing,
  when the committed stretched test language is stale (debug builds), or when a shell script that ships in the APK
  contains CR bytes (the app runs them with the phone's sh, which chokes on CRLF).

  Why -Test uses a copy: the checkout path contains an apostrophe. Gradle hands the test worker JVM its classpath in an
  @argfile (gradle-worker-classpath*.txt) and java parses single quotes in argfiles as quote characters, so every test
  class fails with ClassNotFoundException. Assembling the APK is not affected.
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$FetchParts,
    [Parameter(Position = 0)] [string]$Tag = '',
    [string]$FromApk = '',
    [string]$Sums = '',
    [int]$BuildNumber = 0,
    [switch]$Release,
    [switch]$OwnerDebug,
    [switch]$Install,
    [switch]$Start,
    [switch]$NoCompile,
    [switch]$CheckSigning,
    [switch]$AcknowledgeS2,
    [switch]$AllowReextract,
    [switch]$Test,
    [switch]$NoBuild,
    [switch]$ListDevices,
    [string]$Serial = '',
    [string]$Repo = 'a-z-i-z-z/fhdjdty'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$Repo_Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'build-ids.ps1')
$Dist = Join-Path $Repo_Root 'dist'
$Parts = Join-Path $Dist 'runtime-parts'
$Total = [System.Diagnostics.Stopwatch]::StartNew()
$Utf8 = New-Object System.Text.UTF8Encoding($false)

# Which build this run is about. Package names come only from build-ids.ps1.
if ($Release.IsPresent -and $OwnerDebug.IsPresent) { Write-Host 'ERROR: -Release and -OwnerDebug exclude each other.' -ForegroundColor Red; exit 1 }
$Identity = 'twin'
if ($Release.IsPresent) { $Identity = 'owner' } elseif ($OwnerDebug.IsPresent) { $Identity = 'owner-debug' }
$TargetPackage = $PaTwinPackage
if ($Identity -ne 'twin') { $TargetPackage = $PaOwnerPackage }
$VersionOffset = 0
if ($Identity -ne 'twin') { $VersionOffset = 10000 }
$ApkPath = Join-Path $Repo_Root 'app\build\outputs\apk\debug\app-debug.apk'
$GradleTask = ':app:assembleDebug'
if ($Identity -eq 'owner') {
    $ApkPath = Join-Path $Repo_Root 'app\build\outputs\apk\release\app-release.apk'
    $GradleTask = ':app:assembleRelease'
}

function Write-Info([string]$m) { Write-Host $m }
function Stop-Script([string]$m) { Write-Host ''; Write-Host ('ERROR: ' + $m) -ForegroundColor Red; exit 1 }
# A safety refusal: nothing on the phone was touched. Exit code 3.
function Stop-Refused([string]$m) { Write-Host ''; Write-Host ('REFUSED: ' + $m) -ForegroundColor Yellow; exit 3 }
# The grep-able result lines start in column 0.
function Write-Result([string]$m) { Write-Host $m }

# Runs a step, prints how long it took.
function Invoke-Step([string]$Name, [scriptblock]$Body) {
    Write-Host ('== ' + $Name)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try { & $Body } catch { Write-Host ('   failed after {0:N1}s' -f $sw.Elapsed.TotalSeconds); throw }
    Write-Host ('   done in {0:N1}s' -f $sw.Elapsed.TotalSeconds)
}

function Quote-Arg([string]$a) {
    if ($a -eq '') { return '""' }
    if ($a -match '[\s"]') { return '"' + ($a -replace '"', '\"') + '"' }
    return $a
}

# Small process runner. Start-Process -Wait in Windows PowerShell 5.1 also waits for every descendant, and Process.WaitForExit()
# without an argument waits for the redirected pipes to close; a program that starts a long-lived daemon (`adb devices` starts the
# adb server) holds both open forever. This runner waits for the DIRECT process only (with a timeout), then drains the output.
if (-not ('PaProc' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;
using System.Text;
using System.Threading;
public static class PaProc {
    public static int Run(string exe, string args, string workDir, int timeoutMs, out string stdout, out string stderr, out bool timedOut) {
        ProcessStartInfo psi = new ProcessStartInfo(exe, args);
        psi.UseShellExecute = false; psi.CreateNoWindow = true; psi.WorkingDirectory = workDir;
        psi.RedirectStandardOutput = true; psi.RedirectStandardError = true;
        psi.StandardOutputEncoding = Encoding.UTF8; psi.StandardErrorEncoding = Encoding.UTF8;
        StringBuilder so = new StringBuilder(); StringBuilder se = new StringBuilder();
        Process p = new Process(); p.StartInfo = psi;
        p.OutputDataReceived += delegate(object s, DataReceivedEventArgs e) { if (e.Data != null) { lock (so) { so.Append(e.Data).Append('\n'); } } };
        p.ErrorDataReceived += delegate(object s, DataReceivedEventArgs e) { if (e.Data != null) { lock (se) { se.Append(e.Data).Append('\n'); } } };
        p.Start(); p.BeginOutputReadLine(); p.BeginErrorReadLine();
        timedOut = false; int code;
        if (!p.WaitForExit(timeoutMs)) { timedOut = true; try { p.Kill(); } catch (Exception) { } code = -1; }
        else { code = p.ExitCode; }
        // No argument-less WaitForExit() here (see above). Let the output readers drain until nothing new arrives for ~200 ms.
        int last = -1; int quiet = 0;
        for (int i = 0; i < 25 && quiet < 2; i++) {
            Thread.Sleep(100);
            int len; lock (so) { len = so.Length; } lock (se) { len += se.Length; }
            if (len == last) quiet++; else { quiet = 0; last = len; }
        }
        lock (so) { stdout = so.ToString(); } lock (se) { stderr = se.ToString(); }
        p.Dispose();
        return code;
    }
}
'@
}

# Runs a program and returns ExitCode, Out (stdout), Err (stderr) and Text (both). With -OutFile/-ErrFile the output goes to those files
# instead (long Gradle runs). Never use 2>&1 on native commands: in PS 5.1 that turns stderr lines into errors.
function Invoke-Native([string]$Exe, [string[]]$ArgList, [string]$WorkDir = $Repo_Root, [string]$OutFile = '', [string]$ErrFile = '', [int]$TimeoutMs = 120000) {
    $line = (($ArgList | ForEach-Object { Quote-Arg $_ }) -join ' ')
    if ($OutFile -ne '') {
        if ($ErrFile -eq '') { $ErrFile = $OutFile + '.err' }
        $p = Start-Process -FilePath $Exe -ArgumentList $line -WorkingDirectory $WorkDir -Wait -PassThru -NoNewWindow `
            -RedirectStandardOutput $OutFile -RedirectStandardError $ErrFile
        $o = [System.IO.File]::ReadAllText($OutFile); $e = [System.IO.File]::ReadAllText($ErrFile)
        return New-Object psobject -Property @{ ExitCode = $p.ExitCode; Out = $o; Err = $e; Text = ($o + $e); TimedOut = $false }
    }
    $o = ''; $e = ''; $t = $false
    $code = [PaProc]::Run($Exe, $line, $WorkDir, $TimeoutMs, [ref]$o, [ref]$e, [ref]$t)
    if ($t) { $e += "`n[timed out after " + [int]($TimeoutMs / 1000) + ' s]' }
    return New-Object psobject -Property @{ ExitCode = $code; Out = $o; Err = $e; Text = ($o + $e); TimedOut = $t }
}

function Read-InfoFile([string]$Path) {
    $h = @{}
    foreach ($l in [System.IO.File]::ReadAllLines($Path)) {
        $i = $l.IndexOf('=')
        if ($i -gt 0) { $h[$l.Substring(0, $i)] = $l.Substring($i + 1).Trim() }
    }
    return $h
}

function Get-Sha256([string]$Path) { return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant() }

function Find-Gh {
    $c = Get-Command gh -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    $p = 'C:\Program Files\GitHub CLI\gh.exe'
    if (Test-Path $p) { return $p }
    Stop-Script 'gh (GitHub CLI) was not found; it is needed for -FetchParts. Install it and run "gh auth login".'
}

function Find-Adb {
    $c = Get-Command adb -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    $p = Join-Path (Join-Path (Join-Path $env:LOCALAPPDATA 'Android') 'Sdk') 'platform-tools\adb.exe'
    if (Test-Path $p) { return $p }
    Stop-Script 'adb was not found (not on PATH, not in %LOCALAPPDATA%\Android\Sdk\platform-tools).'
}

# ---- environment -------------------------------------------------------------------------------------------
function Resolve-Toolchain {
    $java = $env:JAVA_HOME
    if (-not $java -or -not (Test-Path (Join-Path $java 'bin\java.exe'))) { $java = 'C:\Program Files\Android\Android Studio\jbr' }
    if (-not (Test-Path (Join-Path $java 'bin\java.exe'))) {
        Stop-Script ('Java not found. Set JAVA_HOME to a JDK 21 (Android Studio ships one: C:\Program Files\Android\Android Studio\jbr). Looked in: ' + $java)
    }
    $sdk = $env:ANDROID_HOME
    if (-not $sdk -or -not (Test-Path $sdk)) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk -or -not (Test-Path $sdk)) { $sdk = Join-Path (Join-Path $env:LOCALAPPDATA 'Android') 'Sdk' }
    if (-not (Test-Path $sdk)) { Stop-Script ('Android SDK not found. Set ANDROID_HOME (default location: ' + $sdk + ').') }
    $missing = @()
    if (-not (Test-Path (Join-Path $sdk 'platforms\android-37.2'))) { $missing += 'platforms;android-37.2' }
    if (-not (Test-Path (Join-Path $sdk 'build-tools\36.0.0'))) { $missing += 'build-tools;36.0.0' }
    if ($missing.Count -gt 0) {
        Stop-Script ('Android SDK pieces missing in ' + $sdk + ': ' + ($missing -join ', ') + '. Install them with sdkmanager "' + ($missing -join '" "') + '" (or Android Studio SDK Manager).')
    }
    $env:JAVA_HOME = $java
    $env:ANDROID_HOME = $sdk
    $ver = (Invoke-Native (Join-Path $java 'bin\java.exe') @('-version')).Text.Split("`n")[0].Trim()
    Write-Info ('   JAVA_HOME    = ' + $java + '   (' + $ver + ')')
    Write-Info ('   ANDROID_HOME = ' + $sdk)
}

# ---- parts ---------------------------------------------------------------------------------------------------
function Test-Parts {
    $need = @(
        'rootfs\rootfs-arm64.tar.xz', 'rootfs\rootfs-arm64.info',
        'proot\libproot.so', 'proot\libproot-loader.so', 'proot\proot-build.info'
    )
    $gone = @($need | Where-Object { -not (Test-Path (Join-Path $Parts $_)) })
    if ($gone.Count -gt 0) {
        Stop-Script ('Runtime parts missing in ' + $Parts + ': ' + ($gone -join ', ') + '. Run with -FetchParts (needs gh and network).')
    }
}

function Get-PartsTagNumber {
    $src = Join-Path $Parts 'source.txt'
    if (-not (Test-Path $src)) { return 0 }
    $h = Read-InfoFile $src
    if ($h.ContainsKey('tag') -and $h['tag'] -match '^phase0-(\d+)$') { return [int]$Matches[1] }
    return 0
}

function Get-ReleaseNumber([string]$t) {
    if ($t -match '^phase0-(\d+)$') { return [int]$Matches[1] }
    return -1
}

function Test-Sums([string]$ApkPath, [string]$SumsPath) {
    if (-not (Test-Path $SumsPath)) { Stop-Script ('SHA256SUMS.txt not found (' + $SumsPath + '); refusing to use an unchecked APK.') }
    $name = [System.IO.Path]::GetFileName($ApkPath)
    $want = $null
    foreach ($l in [System.IO.File]::ReadAllLines($SumsPath)) {
        if ($l -match '^([0-9a-fA-F]{64})\s+\*?(.+)$' -and $Matches[2].Trim() -eq $name) { $want = $Matches[1].ToLowerInvariant() }
    }
    if (-not $want) { Stop-Script ('SHA256SUMS.txt has no entry for ' + $name + '.') }
    $got = Get-Sha256 $ApkPath
    if ($got -ne $want) { Stop-Script ('SHA-256 of ' + $name + ' does not match SHA256SUMS.txt (expected ' + $want + ', got ' + $got + '). Not extracting.') }
    Write-Info ('   SHA-256 checked: ' + $got)
    return $got
}

function Expand-Parts([string]$ApkPath, [string]$ReleaseTag, [string]$ApkSha, [string]$InfoDir) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $stage = $Parts + '.new'
    if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
    New-Item -ItemType Directory -Force (Join-Path $stage 'rootfs') | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $stage 'proot') | Out-Null
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $map = @(
            @('assets/rootfs.tar.xz', 'rootfs\rootfs-arm64.tar.xz'),
            @('lib/arm64-v8a/libproot.so', 'proot\libproot.so'),
            @('lib/arm64-v8a/libproot-loader.so', 'proot\libproot-loader.so')
        )
        foreach ($m in $map) {
            $e = $zip.GetEntry($m[0])
            if (-not $e) { throw ('the APK has no entry ' + $m[0]) }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $stage $m[1]), $true)
        }
        # The .info files: release assets when downloaded; otherwise the copies inside the APK without the stub_* flags.
        $infoMap = @(@('rootfs-arm64.info', 'rootfs\rootfs-arm64.info', 'assets/rootfs.info'), @('proot-build.info', 'proot\proot-build.info', 'assets/proot.info'))
        foreach ($m in $infoMap) {
            $srcFile = ''
            if ($InfoDir -ne '') { $srcFile = Join-Path $InfoDir $m[0] }
            if ($srcFile -ne '' -and (Test-Path $srcFile)) {
                Copy-Item $srcFile (Join-Path $stage $m[1])
            } else {
                $e = $zip.GetEntry($m[2])
                if (-not $e) { throw ('no ' + $m[0] + ' among the release assets and no ' + $m[2] + ' in the APK') }
                $r = New-Object System.IO.StreamReader($e.Open())
                try { $txt = $r.ReadToEnd() } finally { $r.Dispose() }
                $keep = @($txt -split "`r?`n" | Where-Object { $_ -ne '' -and $_ -notmatch '^stub_' })
                [System.IO.File]::WriteAllText((Join-Path $stage $m[1]), (($keep -join "`n") + "`n"), $Utf8)
            }
        }
    } finally { $zip.Dispose() }

    # Check the extracted files against what the build info says about them.
    $ri = Read-InfoFile (Join-Path $stage 'rootfs\rootfs-arm64.info')
    $pi = Read-InfoFile (Join-Path $stage 'proot\proot-build.info')
    $tar = Join-Path $stage 'rootfs\rootfs-arm64.tar.xz'
    if ($ri.ContainsKey('archive_bytes') -and ([int64]$ri['archive_bytes'] -ne (Get-Item $tar).Length)) { throw 'rootfs archive size differs from rootfs-arm64.info' }
    if ($ri.ContainsKey('archive_sha256') -and ($ri['archive_sha256'].ToLowerInvariant() -ne (Get-Sha256 $tar))) { throw 'rootfs archive SHA-256 differs from rootfs-arm64.info' }
    if ($pi.ContainsKey('libproot_sha256') -and ($pi['libproot_sha256'].ToLowerInvariant() -ne (Get-Sha256 (Join-Path $stage 'proot\libproot.so')))) { throw 'libproot.so SHA-256 differs from proot-build.info' }
    if ($pi.ContainsKey('libproot_loader_sha256') -and ($pi['libproot_loader_sha256'].ToLowerInvariant() -ne (Get-Sha256 (Join-Path $stage 'proot\libproot-loader.so')))) { throw 'libproot-loader.so SHA-256 differs from proot-build.info' }
    Write-Info '   parts match their .info files (sizes and SHA-256)'

    $buildId = 'unknown'
    if ($ri.ContainsKey('build_id')) { $buildId = $ri['build_id'] }
    $lines = @(
        ('tag=' + $ReleaseTag),
        ('apk=' + [System.IO.Path]::GetFileName($ApkPath)),
        ('apk_sha256=' + $ApkSha),
        ('rootfs_build_id=' + $buildId),
        ('fetched=' + (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))
    )
    [System.IO.File]::WriteAllText((Join-Path $stage 'source.txt'), (($lines -join "`n") + "`n"), $Utf8)

    if (Test-Path $Parts) { Remove-Item -Recurse -Force $Parts }
    Move-Item $stage $Parts
    Write-Info ('   parts of ' + $ReleaseTag + ' are in ' + $Parts)
}

function Invoke-FetchParts {
    New-Item -ItemType Directory -Force $Dist | Out-Null
    if ($FromApk -ne '') {
        if (-not (Test-Path $FromApk)) { Stop-Script ('APK not found: ' + $FromApk) }
        $apk = (Resolve-Path $FromApk).Path
        $t = $Tag
        $apkName = [System.IO.Path]::GetFileName($apk)
        if ($t -eq '' -and ($apkName -match '-(phase0-\d+)-')) { $t = $Matches[1] }
        if ($t -eq '' -and ($apkName -match '^Agiri-(\d+)')) { $t = 'phase0-' + $Matches[1] }
        if ($t -eq '') { Stop-Script 'Cannot tell which release this APK belongs to; pass -Tag phase0-N.' }
        if ($Sums -ne '') {
            if (-not (Test-Path $Sums)) { Stop-Script ('SHA256SUMS file not found: ' + $Sums) }
            $sumsPath = (Resolve-Path $Sums).Path
        } else {
            $sumsPath = Join-Path ([System.IO.Path]::GetDirectoryName($apk)) 'SHA256SUMS.txt'
        }
        $sha = Test-Sums $apk $sumsPath
        Expand-Parts $apk $t $sha ''
        return
    }
    $gh = Find-Gh
    $t = $Tag
    if ($t -eq '') {
        $r = Invoke-Native $gh @('release', 'list', '--repo', $Repo, '--limit', '100', '--exclude-drafts', '--json', 'tagName')
        if ($r.ExitCode -ne 0) { Stop-Script ('gh release list failed: ' + $r.Text.Trim()) }
        $best = -1
        foreach ($rel in (ConvertFrom-Json $r.Out)) {   # stdout only: warnings on stderr must not break the JSON
            $n = Get-ReleaseNumber ([string]$rel.tagName)
            if ($n -gt $best) { $best = $n; $t = [string]$rel.tagName }
        }
        if ($best -lt 0) { Stop-Script ('No phase0-* release found in ' + $Repo + '.') }
        Write-Info ('   latest release: ' + $t)
    } elseif ((Get-ReleaseNumber $t) -lt 0) {
        Stop-Script ('The tag must look like phase0-8, got: ' + $t)
    }
    $dl = Join-Path $env:TEMP ('agiri-fetch-' + $t)
    if (Test-Path $dl) { Remove-Item -Recurse -Force $dl }
    New-Item -ItemType Directory -Force $dl | Out-Null
    try {
        # A release holds either the twin APK Agiri-N-test.apk (new) or the legacy PocketAgent-phase0-N APK: exactly one of them.
        $r = Invoke-Native $gh @('release', 'download', $t, '--repo', $Repo, '--dir', $dl, '--clobber',
            '--pattern', 'Agiri-*-test.apk', '--pattern', 'PocketAgent-phase0-*.apk', '--pattern', 'SHA256SUMS.txt', '--pattern', '*.info') $Repo_Root '' '' 1800000
        if ($r.ExitCode -ne 0) { Stop-Script ('gh release download ' + $t + ' failed: ' + $r.Text.Trim()) }
        $apkFile = @(Get-ChildItem $dl -Filter '*.apk')
        if ($apkFile.Count -ne 1) { Stop-Script ('Expected one APK (Agiri-N-test.apk or the legacy PocketAgent-phase0-N one) in release ' + $t + ', found ' + $apkFile.Count + '.') }
        Write-Info ('   downloaded ' + $apkFile[0].Name + ' (' + [math]::Round($apkFile[0].Length / 1MB, 1) + ' MB)')
        $sha = Test-Sums $apkFile[0].FullName (Join-Path $dl 'SHA256SUMS.txt')
        Expand-Parts $apkFile[0].FullName $t $sha $dl
    } finally {
        if (Test-Path $dl) { Remove-Item -Recurse -Force $dl -ErrorAction SilentlyContinue }
    }
}

# ---- checks and build ------------------------------------------------------------------------------------------
# Shell scripts that ship in the APK and run under the phone's sh: no CR bytes allowed.
function Test-ShellScript([string]$RelativePath, [bool]$Required) {
    $f = Join-Path $Repo_Root $RelativePath
    if (-not (Test-Path $f)) {
        if ($Required) { Stop-Script ('Not found: ' + $f) }
        return
    }
    $bytes = [System.IO.File]::ReadAllBytes($f)
    if ([Array]::IndexOf($bytes, [byte]13) -ge 0) {
        Stop-Script ($RelativePath + ' contains CR (CRLF line endings). The app runs it with the phone''s sh, which fails on them. Convert it to LF ' +
            '(with .gitattributes in place: git checkout -- ' + ($RelativePath -replace '\\', '/') + ').')
    }
    Write-Info ('   ' + $RelativePath + ' has LF line endings')
}

function Test-Launcher {
    Test-ShellScript 'runtime\pocketagent-proot.sh' $true
    # The practice model and its starter are copied into the APK as assets (app/build.gradle.kts).
    Test-ShellScript 'runtime\practice-start.sh' $true
    $py = Join-Path $Repo_Root 'rootfs\smoke\mock_llm.py'
    if (-not (Test-Path $py)) { Stop-Script ('Not found: ' + $py + ' (the practice model shipped as an asset).') }
}

# The committed stretched test language (app\src\debug\res\values-en-rXZ) must match the English strings; debug builds contain it.
function Test-StretchedLanguage {
    $gen = Join-Path $PSScriptRoot 'make-test-language.ps1'
    if (-not (Test-Path $gen)) {
        Write-Info '   (tools\make-test-language.ps1 not found: stretched language not checked)'
        return
    }
    $ps = Join-Path $PSHOME 'powershell.exe'
    $r = Invoke-Native $ps @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $gen, '-Check') $Repo_Root '' '' 300000
    if ($r.ExitCode -eq 0) { Write-Info '   stretched test language is up to date'; return }
    if ($r.ExitCode -eq 3) {
        Write-Host $r.Text.Trim()
        Stop-Refused ('The committed stretched test language is stale. Regenerate it first: powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1')
    }
    Write-Host $r.Text.Trim()
    Stop-Script ('make-test-language.ps1 -Check failed (exit ' + $r.ExitCode + ').')
}

function Get-GitCommitLabel {
    $git = Get-Command git -ErrorAction SilentlyContinue
    if (-not $git) { return 'local-nogit' }
    $r = Invoke-Native $git.Source @('rev-parse', '--short=7', 'HEAD')
    if ($r.ExitCode -ne 0) { return 'local-nogit' }
    $label = 'local-' + $r.Text.Trim()
    $s = Invoke-Native $git.Source @('status', '--porcelain')
    if ($s.ExitCode -eq 0 -and $s.Text.Trim() -ne '') { $label += '-dirty' }
    return $label
}

function Resolve-BuildNumber {
    $floor = Get-PartsTagNumber
    $bn = $BuildNumber
    if ($bn -eq 0) {
        if ($floor -eq 0) {
            Stop-Script ('Do not know which release the parts in ' + $Parts + ' came from (no source.txt). Pass -BuildNumber <n> (the release number or higher), or run -FetchParts.')
        }
        $bn = $floor
    }
    if ($bn -lt $floor) { Stop-Script ('-BuildNumber ' + $bn + ' is lower than ' + $floor + ', the release the parts came from.') }
    return $bn
}

# ---- the APK and the installed app (aapt2, apksigner) -------------------------------------------------------------
function Get-BuildToolsDir { return (Join-Path (Join-Path $env:ANDROID_HOME 'build-tools') '36.0.0') }

# `aapt2 dump badging`: package, version, debuggable, target SDK.
function Get-ApkBadging([string]$apk) {
    $aapt = Join-Path (Get-BuildToolsDir) 'aapt2.exe'
    if (-not (Test-Path $aapt)) { Stop-Script ('aapt2 not found: ' + $aapt) }
    $r = Invoke-Native $aapt @('dump', 'badging', $apk) $Repo_Root '' '' 120000
    if ($r.ExitCode -ne 0) { Stop-Script ('aapt2 dump badging failed for ' + $apk + ': ' + $r.Text.Trim()) }
    $m = [regex]::Match($r.Out, "package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'")
    if (-not $m.Success) { Stop-Script ('Cannot read the package line of ' + $apk) }
    $t = [regex]::Match($r.Out, "targetSdkVersion:'(\d+)'")
    $target = 0
    if ($t.Success) { $target = [int]$t.Groups[1].Value }
    return New-Object psobject -Property @{
        Package = $m.Groups[1].Value
        VersionCode = [int64]$m.Groups[2].Value
        VersionName = $m.Groups[3].Value
        Debuggable = ($r.Out -match '(?m)^application-debuggable')
        TargetSdk = $target
    }
}

function Get-ApkPackage([string]$apk) {
    $aapt = Join-Path (Get-BuildToolsDir) 'aapt2.exe'
    if (Test-Path $aapt) {
        $r = Invoke-Native $aapt @('dump', 'packagename', $apk) $Repo_Root '' '' 120000
        $name = $r.Out.Trim()
        if ($r.ExitCode -eq 0 -and $name -match '^[A-Za-z0-9_.]+$') { return $name }
    }
    return (Get-ApkBadging $apk).Package
}

# SHA-256 digest of the first signer, lowercase hex; $null when the APK has no valid signature.
function Get-ApkCertSha256([string]$apk) {
    $jar = Join-Path (Join-Path (Get-BuildToolsDir) 'lib') 'apksigner.jar'
    if (-not (Test-Path $jar)) { Stop-Script ('apksigner.jar not found: ' + $jar) }
    $r = Invoke-Native (Join-Path $env:JAVA_HOME 'bin\java.exe') @('-jar', $jar, 'verify', '--print-certs', $apk) $Repo_Root '' '' 300000
    $m = [regex]::Match($r.Out, 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')
    if ($r.ExitCode -ne 0 -or -not $m.Success) { return $null }
    return $m.Groups[1].Value.ToLowerInvariant()
}

# ---- adb ----------------------------------------------------------------------------------------------------------
function Get-AdbBase([string]$adb) {
    $r = Invoke-Native $adb @('devices') $Repo_Root '' '' 60000
    if ($r.ExitCode -ne 0) { Stop-Script ('adb devices failed: ' + $r.Text.Trim()) }
    $devs = @()
    foreach ($l in ($r.Out -split "`r?`n")) {
        if ($l -match '^(\S+)\s+device\s*$') { $devs += $Matches[1] }
    }
    if ($Serial -ne '') {
        if ($devs -notcontains $Serial) { Stop-Script ('Device ' + $Serial + ' is not connected (connected: ' + $(if ($devs.Count -gt 0) { $devs -join ', ' } else { 'none' }) + ').') }
        return @('-s', $Serial)
    }
    if ($devs.Count -eq 0) { Stop-Script 'No phone connected (adb devices lists no device in state "device"). Check the cable and USB debugging authorisation.' }
    if ($devs.Count -gt 1) { Stop-Script ('More than one device connected (' + ($devs -join ', ') + '); pass -Serial <id>.') }
    return @('-s', $devs[0])
}

# $true when exactly one phone (or the -Serial one) is connected. Never exits the script.
function Test-AdbReady {
    $c = Get-Command adb -ErrorAction SilentlyContinue
    $adb = $null
    if ($c) { $adb = $c.Source } else {
        $p = Join-Path (Join-Path (Join-Path $env:LOCALAPPDATA 'Android') 'Sdk') 'platform-tools\adb.exe'
        if (Test-Path $p) { $adb = $p }
    }
    if (-not $adb) { return $false }
    $r = Invoke-Native $adb @('devices') $Repo_Root '' '' 60000
    if ($r.ExitCode -ne 0) { return $false }
    $devs = @()
    foreach ($l in ($r.Out -split "`r?`n")) { if ($l -match '^(\S+)\s+device\s*$') { $devs += $Matches[1] } }
    if ($Serial -ne '') { return ($devs -contains $Serial) }
    return ($devs.Count -eq 1)
}

function Invoke-Adb([string[]]$AdbArgs, [int]$TimeoutMs = 120000) {
    $adb = Find-Adb
    $base = Get-AdbBase $adb
    return (Invoke-Native $adb ($base + $AdbArgs) $Repo_Root '' '' $TimeoutMs)
}

# What `dumpsys package <pkg>` says: installed or not, version code, last update (the phone's clock, read as local time).
function Get-InstalledPackageInfo([string]$pkg) {
    $r = Invoke-Adb @('shell', 'dumpsys', 'package', $pkg) 120000
    $exists = ($r.ExitCode -eq 0) -and ($r.Out -match ('Package \[' + [regex]::Escape($pkg) + '\]'))
    $code = $null
    $updated = $null
    if ($exists) {
        $m = [regex]::Match($r.Out, 'versionCode=(\d+)')
        if ($m.Success) { $code = [int64]$m.Groups[1].Value }
        $u = [regex]::Match($r.Out, 'lastUpdateTime=(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)')
        if ($u.Success) { $updated = [datetime]::ParseExact($u.Groups[1].Value, 'yyyy-MM-dd HH:mm:ss', [System.Globalization.CultureInfo]::InvariantCulture) }
    }
    # Known = the phone really answered. Exists = $false with Known = $false means "could not ask", never "not installed".
    $known = ($r.ExitCode -eq 0) -and (-not $r.TimedOut)
    return New-Object psobject -Property @{ Exists = $exists; Known = $known; VersionCode = $code; LastUpdate = $updated; Text = $r.Out }
}

# $null when the package is not installed.
function Get-InstalledVersionCode([string]$pkg) {
    return (Get-InstalledPackageInfo $pkg).VersionCode
}

# `dumpsys` prints a hash of the certificate bytes, not SHA-256, so the installed base APK is pulled and read with apksigner.
function Get-InstalledCertSha256([string]$pkg) {
    $r = Invoke-Adb @('shell', 'pm', 'path', $pkg) 60000
    $paths = @()
    foreach ($l in ($r.Out -split "`r?`n")) { if ($l -match '^package:(.+)$') { $paths += $Matches[1].Trim() } }
    if ($paths.Count -eq 0) { return $null }
    $remote = $paths[0]
    foreach ($p in $paths) { if ($p.EndsWith('/base.apk')) { $remote = $p } }
    $tmp = Join-Path $env:TEMP ('agiri-installed-' + [guid]::NewGuid().ToString('N') + '.apk')
    try {
        $pull = Invoke-Adb @('pull', $remote, $tmp) 900000
        if ($pull.ExitCode -ne 0 -or -not (Test-Path $tmp)) { Stop-Script ('adb pull of the installed APK failed: ' + $pull.Text.Trim()) }
        return (Get-ApkCertSha256 $tmp)
    } finally {
        if (Test-Path $tmp) { Remove-Item -Force $tmp -ErrorAction SilentlyContinue }
    }
}

# The local build counter for this CI run: 1..99. Version code = (offset + ciRun) * 100 + seq.
#   installed code below the run's base          -> 1
#   installed code inside the run's 99 slots     -> the next slot
#   installed code of a newer CI run             -> stop: the parts are older than the phone
function Resolve-LocalSeq([int]$ciRun, $installedCode, [int]$offset) {
    $base = [int64](($offset + $ciRun) * 100)
    if ($null -eq $installedCode -or [int64]$installedCode -lt $base) { return 1 }
    $c = [int64]$installedCode
    if ($c -gt ($base + 99)) {
        Stop-Script ('The installed app has version code ' + $c + ', from a newer CI run than ' + $ciRun + ' (this run''s codes end at ' + ($base + 99) + '). Fetch newer parts with -FetchParts, or pass a higher -BuildNumber.')
    }
    $next = ($c - $base) + 1
    if ($next -gt 99) { Stop-Script ('All 99 local builds of CI run ' + $ciRun + ' are used up on the phone. Fetch newer parts with -FetchParts.') }
    return [int]$next
}

# The Linux image id the APK carries: assets/rootfs-arm64.info or assets/rootfs.info (build_id), else the parts' source.txt.
function Get-ApkRootfsBuildId([string]$apk) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        foreach ($name in @('assets/rootfs-arm64.info', 'assets/rootfs.info')) {
            $e = $zip.GetEntry($name)
            if ($e) {
                $rd = New-Object System.IO.StreamReader($e.Open())
                try { $txt = $rd.ReadToEnd() } finally { $rd.Dispose() }
                $m = [regex]::Match($txt, '(?m)^build_id=(.+)$')
                if ($m.Success) { return $m.Groups[1].Value.Trim() }
            }
        }
    } finally { $zip.Dispose() }
    $src = Join-Path $Parts 'source.txt'
    if (Test-Path $src) {
        $h = Read-InfoFile $src
        if ($h.ContainsKey('rootfs_build_id')) { return $h['rootfs_build_id'].Trim() }
    }
    return $null
}

# Is there a read-only copy of the owner's data in dist\owner-backup\, newer than $since?
# Accepted: a folder named chat (at most three levels down) with at least one file in it, or a .tar / .zip archive whose
# listing holds a chat folder. "Newer" is the write time of the newest such file or archive.
function Test-OwnerBackup($since) {
    $dir = Join-Path $Dist 'owner-backup'
    if (-not (Test-Path $dir)) { return $false }
    if ($null -eq $since) { return $false }
    $newest = [datetime]::MinValue
    $found = $false
    $chatDirs = @(Get-ChildItem -LiteralPath $dir -Directory -Recurse -Depth 3 -ErrorAction SilentlyContinue | Where-Object { $_.Name -eq 'chat' })
    foreach ($cd in $chatDirs) {
        foreach ($f in @(Get-ChildItem -LiteralPath $cd.FullName -File -Recurse -ErrorAction SilentlyContinue)) {
            $found = $true
            if ($f.LastWriteTime -gt $newest) { $newest = $f.LastWriteTime }
        }
    }
    $archives = @(Get-ChildItem -LiteralPath $dir -File -ErrorAction SilentlyContinue | Where-Object { $_.Extension -eq '.tar' -or $_.Extension -eq '.zip' })
    foreach ($a in $archives) {
        $listing = ''
        if ($a.Extension -eq '.zip') {
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $z = [System.IO.Compression.ZipFile]::OpenRead($a.FullName)
            try { $listing = (($z.Entries | ForEach-Object { $_.FullName }) -join "`n") } finally { $z.Dispose() }
        } else {
            $tarCmd = Get-Command tar -ErrorAction SilentlyContinue
            if ($tarCmd) { $listing = (Invoke-Native $tarCmd.Source @('-tf', $a.FullName) $Repo_Root '' '' 300000).Out }
        }
        if ($listing -match '(?m)(^|/)chat/[^/\r\n]+') {
            $found = $true
            if ($a.LastWriteTime -gt $newest) { $newest = $a.LastWriteTime }
        }
    }
    return ($found -and ($newest -gt $since))
}

# Every install over the owner's app goes through here. Refuses (exit 3) unless:
#   the APK is the owner's package; and either the app is not installed (a fresh install), or
#   the signing certificate is the installed one, the version code is higher, the read-only data copy exists and is newer than
#   the installed app, and the APK's Linux image equals the installed one (-AllowReextract lifts only the last).
function Assert-OwnerInstallAllowed([string]$apk) {
    $badge = Get-ApkBadging $apk
    if (-not (Test-PaOwnerPackage $badge.Package)) { Stop-Refused ('The APK is for ' + $badge.Package + ', not for the owner''s package; refusing an owner install.') }
    $built = Get-ApkCertSha256 $apk
    if (-not $built) { Stop-Refused ('The APK has no valid signature: ' + $apk) }
    $info = Get-InstalledPackageInfo $PaOwnerPackage
    if (-not $info.Known) {
        Stop-Refused 'Could not ask the phone whether the owner''s app is installed (dumpsys failed or timed out); nothing was changed.'
    }
    if (-not $info.Exists) {
        # "Not installed" is believed only when a second, independent question agrees.
        $p = Invoke-Adb @('shell', 'pm', 'list', 'packages', $PaOwnerPackage) 60000
        if ($p.ExitCode -ne 0 -or $p.TimedOut) {
            Stop-Refused 'Could not confirm with pm that the owner''s app is not installed; nothing was changed.'
        }
        $listed = $false
        foreach ($l in ($p.Out -split "`r?`n")) { if ($l.Trim() -eq ('package:' + $PaOwnerPackage)) { $listed = $true } }
        if ($listed) {
            Stop-Refused ('pm lists ' + $PaOwnerPackage + ' but dumpsys did not describe it; refusing to treat it as a fresh install. Nothing was changed.')
        }
        Write-Info ('   NOTICE: ' + $PaOwnerPackage + ' is not installed on this phone: this is a fresh install, nothing to protect.')
        return
    }
    $installedCert = Get-InstalledCertSha256 $PaOwnerPackage
    if (-not $installedCert) { Stop-Refused 'Could not read the certificate of the installed app; nothing was changed.' }
    if ($installedCert -ne $built) {
        Stop-Refused ('The APK is signed with another key than the installed app (installed ' + $installedCert + ', APK ' + $built + '). Android would refuse the update; the app is never removed to make it work.')
    }
    if ($null -eq $info.VersionCode -or $badge.VersionCode -le $info.VersionCode) {
        Stop-Refused ('The APK version code ' + $badge.VersionCode + ' is not higher than the installed ' + $info.VersionCode + '. Build with a higher -BuildNumber, or let the script pick the next local number.')
    }
    if (-not (Test-OwnerBackup $info.LastUpdate)) {
        Stop-Refused ('There is no read-only copy of the owner''s data in ' + (Join-Path $Dist 'owner-backup') + ' that is newer than the installed app (updated ' + $info.LastUpdate + '). Take the copy first (chats, bots, memory, settings; no credentials).')
    }
    $apkImage = Get-ApkRootfsBuildId $apk
    $r = Invoke-Adb @('shell', 'run-as', $PaOwnerPackage, 'cat', 'files/rootfs.stamp') 60000
    $stamp = $r.Out.Trim()
    if ($AllowReextract.IsPresent) {
        Write-Info '   NOTICE: -AllowReextract given: the Linux image check is skipped.'
    } elseif ($r.ExitCode -ne 0 -or $stamp -eq '' -or $stamp -match 'run-as:|not debuggable|Unknown package') {
        Stop-Refused ('Could not read files/rootfs.stamp of the installed app (' + $r.Text.Trim() + '), so the Linux image cannot be compared. Nothing was changed. -AllowReextract lifts this refusal and is not meant for Wave 0.')
    } elseif (-not $apkImage -or $apkImage -ne $stamp) {
        Stop-Refused ('The APK carries Linux image ' + $apkImage + ' but the phone has ' + $stamp + ': installing would extract the image again and move the agent''s data. Use parts from the same release as the installed app, or -AllowReextract (not meant for Wave 0).')
    }
    Write-Info '   owner install allowed: same certificate, higher version code, data copy present, same Linux image'
}

function Get-CompileState([string]$pkg) {
    $r = Invoke-Adb @('shell', 'dumpsys', 'package', $pkg) 120000
    $m = [regex]::Match($r.Out, 'arm64[^\r\n]*\[status=([A-Za-z\-]+)\]')
    if ($m.Success) { return $m.Groups[1].Value }
    return 'unknown'
}

function Invoke-CompileSpeed([string]$pkg) {
    Write-Info ('   compiling ' + $pkg + ' (cmd package compile -m speed -f), this takes a while')
    $r = Invoke-Adb @('shell', 'cmd', 'package', 'compile', '-m', 'speed', '-f', $pkg) 1500000
    if ($r.ExitCode -ne 0 -or $r.Text -notmatch 'Success') { Write-Info ('   compile did not report Success: ' + $r.Text.Trim()) }
}

# ---- build --------------------------------------------------------------------------------------------------------
function Invoke-Build {
    Test-Launcher
    Test-Parts
    if ($Identity -ne 'owner') { Test-StretchedLanguage }
    $bn = Resolve-BuildNumber
    $commit = Get-GitCommitLabel
    $seq = 1
    if (Test-AdbReady) {
        $seq = Resolve-LocalSeq $bn (Get-InstalledVersionCode $TargetPackage) $VersionOffset
    } else {
        Write-Info '   (no single phone connected: assuming local build number 1)'
    }
    $rootfs = Join-Path $Parts 'rootfs\rootfs-arm64.tar.xz'
    $proot = Join-Path $Parts 'proot'
    $log = Join-Path $Dist 'local-build.log'
    New-Item -ItemType Directory -Force $Dist | Out-Null
    Write-Info ('   build = ' + $Identity + ', ciRun = ' + $bn + ', localSeq = ' + $seq + ', gitCommit = ' + $commit)
    Write-Info ('   parts from  = ' + $(if (Test-Path (Join-Path $Parts 'source.txt')) { (Read-InfoFile (Join-Path $Parts 'source.txt'))['tag'] } else { '(unrecorded)' }))
    $gradle = Join-Path $Repo_Root 'gradlew.bat'
    $a = @('--no-daemon', $GradleTask, ('-ProotfsArchive=' + $rootfs), ('-PprootDir=' + $proot), ('-PciRun=' + $bn), ('-PlocalSeq=' + $seq), ('-PgitCommit=' + $commit))
    if ($Identity -eq 'owner-debug') { $a += '-PownerDebug=true' }
    # A product left over from an earlier build must never be mistaken for this one.
    if (Test-Path $ApkPath) { Remove-Item -Force $ApkPath }
    # Start-Process with -WorkingDirectory: "cmd /c gradlew.bat" breaks on the apostrophe in the checkout path.
    $r = Invoke-Native $gradle $a $Repo_Root $log ($log + '.err')
    if ($r.ExitCode -ne 0) {
        Write-Host ('--- last lines of ' + $log + ' ---')
        $all = $r.Text -split "`r?`n"
        $all | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }
        Stop-Script ('Gradle failed (exit ' + $r.ExitCode + '). Full log: ' + $log)
    }
    if (-not (Test-Path $ApkPath)) { Stop-Script ('Gradle reported success but ' + $ApkPath + ' does not exist.') }
    Show-ApkFacts $ApkPath
}

# Prints the grep-able lines and checks that the file is the build that was asked for.
function Show-ApkFacts([string]$apk) {
    $item = Get-Item $apk
    $badge = Get-ApkBadging $apk
    $cert = Get-ApkCertSha256 $apk
    Write-Result ('APK: ' + $apk)
    Write-Result ('PACKAGE: ' + $badge.Package)
    Write-Result ('VERSION: ' + $badge.VersionName + ' (' + $badge.VersionCode + ')')
    Write-Result ('CERT-SHA256: ' + $(if ($cert) { $cert } else { 'none' }))
    Write-Info ('   size ' + [math]::Round($item.Length / 1MB, 1) + ' MB, SHA-256 ' + (Get-Sha256 $apk))
    if ($badge.Package -ne $TargetPackage) {
        Stop-Script ('The APK is for ' + $badge.Package + ' but this run builds ' + $Identity + ' (' + $TargetPackage + '). A stale file at ' + $apk + '?')
    }
    $wantDebuggable = ($Identity -ne 'owner')
    if ($badge.Debuggable -ne $wantDebuggable) {
        Stop-Script ('The APK has debuggable = ' + $badge.Debuggable + ' but the ' + $Identity + ' build must have ' + $wantDebuggable + '. A stale file at ' + $apk + '?')
    }
    if ($badge.TargetSdk -ne 28) { Stop-Script ('The APK targets SDK ' + $badge.TargetSdk + ', not 28: the extracted Linux image could not run.') }
}

# ---- tests ------------------------------------------------------------------------------------------------------
function Invoke-TestCopy {
    $copy = Join-Path $env:TEMP 'agiri-test-copy'
    if ($copy.Contains("'")) { Stop-Script ('The temp folder path contains an apostrophe too (' + $copy + '); set TEMP to another folder.') }
    New-Item -ItemType Directory -Force $copy | Out-Null
    # Mirror the sources; build output and Gradle state in the copy are kept between runs (excluded dirs are not purged), so reruns are fast.
    $rc = Invoke-Native 'robocopy.exe' @($Repo_Root, $copy, '/MIR', '/XD', '.git', '.gradle', 'build', 'dist', '.idea', '.kotlin', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/R:1', '/W:1') $Repo_Root '' '' 600000
    if ($rc.TimedOut -or $rc.ExitCode -lt 0 -or $rc.ExitCode -ge 8) { Stop-Script ('robocopy failed (exit ' + $rc.ExitCode + '): ' + $rc.Text.Trim()) }
    $log = Join-Path $Dist 'local-test.log'
    New-Item -ItemType Directory -Force $Dist | Out-Null
    # The copy keeps its build directory between runs: delete the old results first, so a compile error can never show
    # the previous run's green summary.
    $dir = Join-Path $copy 'app\build\test-results\testDebugUnitTest'
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
    $gradleArgs = @('--no-daemon', ':app:testDebugUnitTest', '--continue')
    if ($Identity -eq 'owner-debug') { $gradleArgs += '-PownerDebug=true' }
    $r = Invoke-Native (Join-Path $copy 'gradlew.bat') $gradleArgs $copy $log ($log + '.err')
    $files = @()
    if (Test-Path $dir) { $files = @(Get-ChildItem $dir -Filter '*.xml') }
    if ($files.Count -eq 0) {
        Write-Host ('--- last lines of ' + $log + ' (Gradle exit ' + $r.ExitCode + ') ---')
        ($r.Text -split "`r?`n") | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }
        Stop-Script ('No fresh test results were produced (Gradle exit ' + $r.ExitCode + '). Log: ' + $log)
    }
    $tests = 0; $failed = 0; $skipped = 0; $windowsOnly = 0; $unexpected = 0
    foreach ($f in $files) {
        [xml]$x = [System.IO.File]::ReadAllText($f.FullName)
        $suite = $x.testsuite
        $tests += [int]$suite.tests; $skipped += [int]$suite.skipped
        foreach ($c in @($suite.testcase)) {
            if ($c.PSObject.Properties['failure'] -and $c.failure) {
                $failed++
                $rawWhy = [string]$c.failure.message
                $why = if ($rawWhy.Length -gt 110) { $rawWhy.Substring(0, 110) + '...' } else { $rawWhy }
                # Symlink creation without the privilege: java.nio.file.FileSystemException in the two suites that make symlinks (English or Turkish Windows).
                $known = ([string]$c.failure.type -eq 'java.nio.file.FileSystemException') -and
                    (@('dev.pocketagent.app.TarExtractorTest', 'dev.pocketagent.app.FileAndUserStateTest') -contains [string]$suite.name) -and
                    ($rawWhy -imatch 'required privilege|symlink|A required privilege is not held|Gereken ayr')
                if ($known) { $windowsOnly++; $label = 'expected on Windows (symlink privilege)' } else { $unexpected++; $label = 'UNEXPECTED' }
                Write-Host ('   FAILED [' + $label + '] ' + $suite.name + ' > ' + $c.name + ' : ' + $why)
            }
        }
    }
    Write-Info ('   tests: ' + $tests + ', failed: ' + $failed + ' (' + $windowsOnly + ' expected on Windows, ' + $unexpected + ' unexpected), skipped: ' + $skipped + '  (variant: ' + $Identity + ', copy: ' + $copy + ', log: ' + $log + ')')
    if ($windowsOnly -gt 0) {
        Write-Info '   Tests that create symlinks need the symlink privilege: enable Windows Developer Mode or run elevated.'
    }
    # Gradle failing while no test failed means something else broke (a task other than the tests).
    if ($r.ExitCode -ne 0 -and $failed -eq 0) {
        ($r.Text -split "`r?`n") | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }
        Stop-Script ('Gradle exited with ' + $r.ExitCode + ' although no test failed. Log: ' + $log)
    }
    if ($unexpected -gt 0) { Stop-Script ([string]$unexpected + ' test(s) failed for reasons other than the Windows symlink privilege. Log: ' + $log) }
    if ($windowsOnly -gt 0) { $script:TestFailures = $windowsOnly }
}

# ---- adb actions --------------------------------------------------------------------------------------------------
function Invoke-ListDevices {
    $adb = Find-Adb
    $r = Invoke-Native $adb @('devices', '-l') $Repo_Root '' '' 60000
    if ($r.ExitCode -ne 0) { Stop-Script ('adb devices failed: ' + $r.Text.Trim()) }
    foreach ($l in ($r.Out -split "`r?`n")) { if ($l.Trim() -ne '') { Write-Info ('   ' + $l.Trim()) } }
}

function Invoke-CheckSigning {
    if (-not (Test-Path $ApkPath)) { Stop-Script ('No APK at ' + $ApkPath + '; build first.') }
    $built = Get-ApkCertSha256 $ApkPath
    Write-Result ('CERT-SHA256: ' + $(if ($built) { $built } else { 'none' }))
    $info = Get-InstalledPackageInfo $TargetPackage
    if (-not $info.Exists) {
        Write-Result 'INSTALLED-CERT-SHA256: none'
        Write-Info ('   ' + $TargetPackage + ' is not installed.')
        exit 3
    }
    $installed = Get-InstalledCertSha256 $TargetPackage
    Write-Result ('INSTALLED-CERT-SHA256: ' + $(if ($installed) { $installed } else { 'none' }))
    if ($built -and $installed -and $built -eq $installed) { Write-Info '   same certificate'; exit 0 }
    Write-Info '   the certificates differ (or one could not be read)'
    exit 3
}

function Invoke-Install {
    if (-not (Test-Path $ApkPath)) { Stop-Script ('No APK at ' + $ApkPath + '; build first.') }
    # Stale-file guard: the package of the file must be the one this run is about.
    $pkg = Get-ApkPackage $ApkPath
    if ($pkg -ne $TargetPackage) {
        Stop-Script ('The APK at ' + $ApkPath + ' is for ' + $pkg + ', but this run installs ' + $Identity + ' (' + $TargetPackage + '). Build again.')
    }
    $badge = Get-ApkBadging $ApkPath
    $wantDebuggable = ($Identity -ne 'owner')
    if ($badge.Debuggable -ne $wantDebuggable) {
        Stop-Script ('The APK at ' + $ApkPath + ' has debuggable = ' + $badge.Debuggable + ', not what the ' + $Identity + ' build must be. Build again.')
    }
    if ($Identity -eq 'owner' -and -not $AcknowledgeS2.IsPresent) {
        Stop-Refused 'The optimised owner build goes onto the owner''s phone only after experiment S2 passed in the twin. Pass -AcknowledgeS2 once that is true.'
    }
    if (Test-PaOwnerPackage $pkg) { Assert-OwnerInstallAllowed $ApkPath }
    $adb = Find-Adb
    $base = Get-AdbBase $adb
    Write-Info ('   adb install -r of ' + $pkg + ' on ' + $base[1])
    $r = Invoke-Native $adb ($base + @('install', '-r', $ApkPath)) $Repo_Root '' '' 900000
    if ($r.ExitCode -ne 0 -or $r.Text -notmatch 'Success') {
        if ($r.Text -match 'INSTALL_FAILED_VERSION_DOWNGRADE' -and $pkg -eq $PaTwinPackage) {
            Write-Info '   version downgrade refused; the test twin may be downgraded, retrying'
            if ($pkg -eq $PaTwinPackage) { $r = Invoke-Native $adb ($base + @('install', '-r', '-d', $ApkPath)) $Repo_Root '' '' 900000 }
        }
    }
    if ($r.ExitCode -ne 0 -or $r.Text -notmatch 'Success') {
        if ($r.Text -match 'INSTALL_FAILED_VERIFICATION_FAILURE') {
            Stop-Script 'Play Protect blocked the first install of the test twin. On the phone tap ''Install anyway'' once (the owner''s decision); updates of an installed twin are not asked again.'
        }
        Stop-Script ('adb install failed: ' + $r.Text.Trim())
    }
    Write-Info '   installed (Success)'
    if ($Identity -eq 'owner' -and -not $NoCompile.IsPresent) { Invoke-CompileSpeed $pkg }
    Write-Result ('COMPILE: ' + (Get-CompileState $pkg))
}

function Invoke-Launch {
    $adb = Find-Adb
    $base = Get-AdbBase $adb
    $r = Invoke-Native $adb ($base + @('shell', 'am', 'start', '-n', (Get-PaComponent $TargetPackage $PaMainActivity))) $Repo_Root '' '' 60000
    if ($r.ExitCode -ne 0 -or $r.Text -match 'Error') { Stop-Script ('could not start the app: ' + $r.Text.Trim()) }
    Write-Info '   app started'
}

# ---- main -----------------------------------------------------------------------------------------------------------
$script:TestFailures = 0
$doFetch = $FetchParts.IsPresent -or ($FromApk -ne '')
$doInstall = $Install.IsPresent -or $Start.IsPresent
$doBuild = -not $NoBuild.IsPresent
if (($Test.IsPresent -or $ListDevices.IsPresent -or $CheckSigning.IsPresent) -and -not $doFetch -and -not $doInstall -and -not $PSBoundParameters.ContainsKey('NoBuild')) {
    $doBuild = $false   # -Test, -ListDevices or -CheckSigning alone only do that
}

try {
    if ($doFetch) { Invoke-Step 'fetch runtime parts' { Invoke-FetchParts } }
    if ($doBuild -or $Test.IsPresent -or $doInstall -or $CheckSigning.IsPresent) { Invoke-Step 'check Java and Android SDK' { Resolve-Toolchain } }
    if ($doBuild) { Invoke-Step ('build APK (' + $Identity + ': gradle ' + $GradleTask + ')') { Invoke-Build } }
    if ($Test.IsPresent) { Invoke-Step 'unit tests (apostrophe-free copy)' { Invoke-TestCopy } }
    if ($ListDevices.IsPresent) { Invoke-Step 'list devices' { Invoke-ListDevices } }
    if ($CheckSigning.IsPresent) { Invoke-Step 'compare signing certificates' { Invoke-CheckSigning } }
    if ($doInstall) { Invoke-Step 'install on the phone' { Invoke-Install } }
    if ($Start.IsPresent) { Invoke-Step 'launch the app' { Invoke-Launch } }
} catch {
    Write-Host ''
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host ('Total {0:N1}s' -f $Total.Elapsed.TotalSeconds)
if ($script:TestFailures -gt 0) { exit 2 }
exit 0
