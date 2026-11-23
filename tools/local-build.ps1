<#
.SYNOPSIS
  Local build of the PocketAgent APK in about 20 seconds, using the rootfs and proot taken from a CI release.

.DESCRIPTION
  CI needs about 10 minutes per push because it builds proot and the Debian rootfs. Neither changes between most
  pushes, so a local build reuses them: the parts come out of an APK of a published phase0-N release.

  Run it (script execution is disabled on this PC, so always go through -ExecutionPolicy Bypass):
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1 [switches]

  Switches
    -FetchParts [phase0-N]   download that release (default: the latest phase0-* release) with gh, verify the APK
                             against the release's SHA256SUMS.txt, extract rootfs + proot into dist\runtime-parts\,
                             check them against the .info files and record the tag in dist\runtime-parts\source.txt.
                             Builds afterwards unless -NoBuild is given. (The tag is a plain second word, or -Tag.)
    -FromApk <file>          like -FetchParts but from a local APK (needs SHA256SUMS.txt next to it, or -Sums).
    -BuildNumber <n>         versionCode / "0.0.<n>-phase0". Default: the number of the release the parts came from.
                             Refused when lower than that number.
    -Install                 adb install -r the built APK on the one connected phone (-Serial <id> to pick one).
                             Retries with -d when Android reports a version downgrade.
    -Start                   -Install, then launch the app.
    -Test                    run :app:testDebugUnitTest from an apostrophe-free copy of the tree (see the note below).
    -NoBuild                 skip the build (e.g. -FetchParts -NoBuild, or -Install of the APK built last time).
    -ListDevices             only list the phones adb sees (starts the adb server if needed); installs nothing.

  The only positional parameter is the tag: "-FetchParts phase0-8". Any other stray word is an error.

  A plain run builds app\build\outputs\apk\debug\app-debug.apk. It stops with a clear message when Java or the Android
  SDK pieces are missing, when the parts are missing, or when runtime\pocketagent-proot.sh contains CR bytes (the app
  ships that file as an asset and runs it with the phone's sh, which chokes on CRLF).

  Why -Test uses a copy: the checkout path contains an apostrophe (C:\Claude's_WorkSpace). Gradle hands the test
  worker JVM its classpath in an @argfile (gradle-worker-classpath*.txt) and java parses single quotes in argfiles as
  quote characters, so every test class fails with ClassNotFoundException. Assembling the APK is not affected.
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$FetchParts,
    [Parameter(Position = 0)] [string]$Tag = '',
    [string]$FromApk = '',
    [string]$Sums = '',
    [int]$BuildNumber = 0,
    [switch]$Install,
    [switch]$Start,
    [switch]$Test,
    [switch]$NoBuild,
    [switch]$ListDevices,
    [string]$Serial = '',
    [string]$Repo = 'a-z-i-z-z/fhdjdty'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$Repo_Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Dist = Join-Path $Repo_Root 'dist'
$Parts = Join-Path $Dist 'runtime-parts'
$AppId = 'dev.pocketagent.app'
$Total = [System.Diagnostics.Stopwatch]::StartNew()
$Utf8 = New-Object System.Text.UTF8Encoding($false)

function Write-Info([string]$m) { Write-Host $m }
function Stop-Script([string]$m) { Write-Host ''; Write-Host ('ERROR: ' + $m) -ForegroundColor Red; exit 1 }

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
    if (-not (Test-Path $SumsPath)) { Stop-Script ('SHA256SUMS.txt not found (' + $SumsPath + '); refusing to use an unverified APK.') }
    $name = [System.IO.Path]::GetFileName($ApkPath)
    $want = $null
    foreach ($l in [System.IO.File]::ReadAllLines($SumsPath)) {
        if ($l -match '^([0-9a-fA-F]{64})\s+\*?(.+)$' -and $Matches[2].Trim() -eq $name) { $want = $Matches[1].ToLowerInvariant() }
    }
    if (-not $want) { Stop-Script ('SHA256SUMS.txt has no entry for ' + $name + '.') }
    $got = Get-Sha256 $ApkPath
    if ($got -ne $want) { Stop-Script ('SHA-256 of ' + $name + ' does not match SHA256SUMS.txt (expected ' + $want + ', got ' + $got + '). Not extracting.') }
    Write-Info ('   SHA-256 verified: ' + $got)
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
        if ($t -eq '' -and ([System.IO.Path]::GetFileName($apk) -match '-(phase0-\d+)-')) { $t = $Matches[1] }
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
    $dl = Join-Path $env:TEMP ('pocketagent-fetch-' + $t)
    if (Test-Path $dl) { Remove-Item -Recurse -Force $dl }
    New-Item -ItemType Directory -Force $dl | Out-Null
    try {
        $r = Invoke-Native $gh @('release', 'download', $t, '--repo', $Repo, '--dir', $dl, '--clobber',
            '--pattern', '*.apk', '--pattern', 'SHA256SUMS.txt', '--pattern', '*.info') $Repo_Root '' '' 1800000
        if ($r.ExitCode -ne 0) { Stop-Script ('gh release download ' + $t + ' failed: ' + $r.Text.Trim()) }
        $apkFile = @(Get-ChildItem $dl -Filter '*.apk')
        if ($apkFile.Count -ne 1) { Stop-Script ('Expected one APK in release ' + $t + ', found ' + $apkFile.Count + '.') }
        Write-Info ('   downloaded ' + $apkFile[0].Name + ' (' + [math]::Round($apkFile[0].Length / 1MB, 1) + ' MB)')
        $sha = Test-Sums $apkFile[0].FullName (Join-Path $dl 'SHA256SUMS.txt')
        Expand-Parts $apkFile[0].FullName $t $sha $dl
    } finally {
        if (Test-Path $dl) { Remove-Item -Recurse -Force $dl -ErrorAction SilentlyContinue }
    }
}

# ---- checks and build ------------------------------------------------------------------------------------------
function Test-Launcher {
    $f = Join-Path $Repo_Root 'runtime\pocketagent-proot.sh'
    if (-not (Test-Path $f)) { Stop-Script ('Launcher not found: ' + $f) }
    $bytes = [System.IO.File]::ReadAllBytes($f)
    if ([Array]::IndexOf($bytes, [byte]13) -ge 0) {
        Stop-Script ('runtime\pocketagent-proot.sh contains CR (CRLF line endings). The app runs it with the phone''s sh, which fails on them. Convert it: ' +
            '(Get-Content -Raw runtime\pocketagent-proot.sh) -replace "`r`n","`n" | Set-Content -NoNewline -Encoding ASCII runtime\pocketagent-proot.sh   (or: git checkout -- runtime/pocketagent-proot.sh with .gitattributes in place).')
    }
    Write-Info '   launcher has LF line endings'
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

function Invoke-Build {
    Test-Launcher
    Test-Parts
    $bn = Resolve-BuildNumber
    $commit = Get-GitCommitLabel
    $rootfs = Join-Path $Parts 'rootfs\rootfs-arm64.tar.xz'
    $proot = Join-Path $Parts 'proot'
    $log = Join-Path $Dist 'local-build.log'
    New-Item -ItemType Directory -Force $Dist | Out-Null
    Write-Info ('   buildNumber = ' + $bn + ', gitCommit = ' + $commit)
    Write-Info ('   parts from  = ' + $(if (Test-Path (Join-Path $Parts 'source.txt')) { (Read-InfoFile (Join-Path $Parts 'source.txt'))['tag'] } else { '(unrecorded)' }))
    $gradle = Join-Path $Repo_Root 'gradlew.bat'
    $a = @('--no-daemon', ':app:assembleDebug', ('-ProotfsArchive=' + $rootfs), ('-PprootDir=' + $proot), ('-PbuildNumber=' + $bn), ('-PgitCommit=' + $commit))
    # Start-Process with -WorkingDirectory: "cmd /c gradlew.bat" breaks on the apostrophe in C:\Claude's_WorkSpace.
    $r = Invoke-Native $gradle $a $Repo_Root $log ($log + '.err')
    if ($r.ExitCode -ne 0) {
        Write-Host ('--- last lines of ' + $log + ' ---')
        $all = $r.Text -split "`r?`n"
        $all | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }
        Stop-Script ('Gradle failed (exit ' + $r.ExitCode + '). Full log: ' + $log)
    }
    $apk = Join-Path $Repo_Root 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { Stop-Script ('Gradle reported success but ' + $apk + ' does not exist.') }
    $item = Get-Item $apk
    Write-Info ('   APK: ' + $apk)
    Write-Info ('   size ' + [math]::Round($item.Length / 1MB, 1) + ' MB, SHA-256 ' + (Get-Sha256 $apk) + ', log ' + $log)
}

# ---- tests ------------------------------------------------------------------------------------------------------
function Invoke-TestCopy {
    $copy = Join-Path $env:TEMP 'pocketagent-test-copy'
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
    $r = Invoke-Native (Join-Path $copy 'gradlew.bat') @('--no-daemon', ':app:testDebugUnitTest', '--continue') $copy $log ($log + '.err')
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
    Write-Info ('   tests: ' + $tests + ', failed: ' + $failed + ' (' + $windowsOnly + ' expected on Windows, ' + $unexpected + ' unexpected), skipped: ' + $skipped + '  (copy: ' + $copy + ', log: ' + $log + ')')
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

function Invoke-ListDevices {
    $adb = Find-Adb
    $r = Invoke-Native $adb @('devices', '-l') $Repo_Root '' '' 60000
    if ($r.ExitCode -ne 0) { Stop-Script ('adb devices failed: ' + $r.Text.Trim()) }
    foreach ($l in ($r.Out -split "`r?`n")) { if ($l.Trim() -ne '') { Write-Info ('   ' + $l.Trim()) } }
}

function Invoke-Install {
    $apk = Join-Path $Repo_Root 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { Stop-Script ('No APK at ' + $apk + '; build first.') }
    $adb = Find-Adb
    $base = Get-AdbBase $adb
    Write-Info ('   adb install -r on ' + $base[1])
    $r = Invoke-Native $adb ($base + @('install', '-r', $apk)) $Repo_Root '' '' 900000
    if ($r.ExitCode -ne 0 -or $r.Text -notmatch 'Success') {
        if ($r.Text -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
            Write-Info '   version downgrade refused; retrying with -d'
            $r = Invoke-Native $adb ($base + @('install', '-r', '-d', $apk)) $Repo_Root '' '' 900000
        }
    }
    if ($r.ExitCode -ne 0 -or $r.Text -notmatch 'Success') { Stop-Script ('adb install failed: ' + $r.Text.Trim()) }
    Write-Info '   installed (Success)'
}

function Invoke-Launch {
    $adb = Find-Adb
    $base = Get-AdbBase $adb
    $r = Invoke-Native $adb ($base + @('shell', 'am', 'start', '-n', ($AppId + '/' + $AppId + '.MainActivity')))
    if ($r.ExitCode -ne 0 -or $r.Text -match 'Error') { Stop-Script ('could not start the app: ' + $r.Text.Trim()) }
    Write-Info '   app started'
}

# ---- main -----------------------------------------------------------------------------------------------------------
$script:TestFailures = 0
$doFetch = $FetchParts.IsPresent -or ($FromApk -ne '')
$doInstall = $Install.IsPresent -or $Start.IsPresent
$doBuild = -not $NoBuild.IsPresent
if (($Test.IsPresent -or $ListDevices.IsPresent) -and -not $doFetch -and -not $doInstall -and -not $PSBoundParameters.ContainsKey('NoBuild')) {
    $doBuild = $false   # -Test or -ListDevices alone only do that
}

try {
    if ($doFetch) { Invoke-Step 'fetch runtime parts' { Invoke-FetchParts } }
    if ($doBuild -or $Test.IsPresent) { Invoke-Step 'check Java and Android SDK' { Resolve-Toolchain } }
    if ($doBuild) { Invoke-Step 'build APK (gradle assembleDebug)' { Invoke-Build } }
    if ($Test.IsPresent) { Invoke-Step 'unit tests (apostrophe-free copy)' { Invoke-TestCopy } }
    if ($ListDevices.IsPresent) { Invoke-Step 'list devices' { Invoke-ListDevices } }
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
