# The phone robot's engine (Wave 0, area ROBOT-T). Dot-sourced by phone-test.ps1 and phone-perf.ps1 AFTER build-ids.ps1:
#
#     . (Join-Path $PSScriptRoot 'build-ids.ps1')
#     . (Join-Path $PSScriptRoot 'phone-lib.ps1')
#
# It defines functions only (no side effects at load; the process helper type is compiled the first time an adb call needs it).
# Runs on Windows PowerShell 5.1 and pwsh 7 (CI runs the -SelfTest of the two tools on Linux pwsh).
#
# What is in here
#   1  small helpers (JSON, files, quoting, property access)
#   2  adb: finding it, running it, the one place that spells a device command
#   3  SAFETY: Assert-PaTwinTarget and Set-PaSystemScales (the only functions that may change data or system settings)
#   4  the UI dump: parsing, selectors, automatic checks (spoken label, cut text)
#   5  the step engine: placeholders, waits, every op of the vocabulary, scenarios, reports
#   6  frame statistics (shared with phone-perf.ps1)
#   7  self test
#
# House rules: host paths are separate array elements or go through Quote-PaArg; no Invoke-Expression; -LiteralPath; device commands are ONE
# argument with single quotes inside; no host path reaches a device command; PNGs by screencap + adb pull (never exec-out); component names
# only through Get-PaComponent (build-ids.ps1). The robot never uninstalls, never clears data on any package but the twin, never changes
# a verifier or install-permission setting.

$script:PaLibDir = $PSScriptRoot

# The vocabulary of scenario files (tools/scenarios/README.md). ScenarioFilesTest keeps its own copy: phone-test.ps1 -SelfTest compares the two.
$script:PaOps = @(
    'link', 'tap', 'type', 'wait', 'expect', 'expectNot', 'shot', 'kill', 'forceStop', 'notification', 'noCrash',
    'key', 'swipe', 'wipe', 'pref', 'seed', 'killGuest', 'expectFile', 'expectColor', 'pick', 'install', 'adb',
    'scale', 'a11y', 'restore', 'scanDump', 'eachRoute', 'measure'
)
# Ops that may only run on the twin (each implementation starts with Assert-PaTwinTarget).
$script:PaTwinOps = @('wipe', 'pref', 'seed', 'killGuest', 'expectFile', 'restore', 'install', 'pick', 'adb')
# The practice model's magic words. A run on the owner's package never types any of them except LONG and PAUSE.
$script:PaKeywords = @('RUN_TOOL', 'ASK_APPROVAL', 'LONG', 'THINK', 'SLOW', 'FAIL', 'MEMORY', 'FORGE', 'MEDIA', 'PROMPT_HAS', 'PROMPT_SIZE', 'BALLAST', 'TOOLS', 'STATS', 'PAUSE')

$script:Pa = @{
    Adb = ''; Serial = ''; Package = ''; Owner = $false; AllowSystemSettings = $false
    OutDir = ''; ScenarioDir = ''; Vars = @{}
    NoA11y = $false; NoCut = $false; ChecksOn = $true; CutMode = $false
    StartStamp = ''; StepNo = 0; ShotNo = 0; ScenarioName = ''
    SeenScreens = @{}; A11y = $null; Cut = $null; A11yAllow = @(); CutAllow = @()
    LogcatText = ''; LastDump = $null; ScreenW = 0; ScreenH = 0
    ScalesTouched = $false; FunctionalScales = $false; Sampler = $null; Hook = $null; LastDumpText = ''; PracticeTurnedOn = $false; TapCache = @{}; Leaks = $null; ScenarioScaled = $false; CapturedVars = $null
    # the focus guard (section 3b): Halted blocks every input until a link brings the app back; FocusRefused stays set for the runners; CurrentOp
    # is the op of the step that runs now; InputOk is true only while Invoke-PaInput sends its one device command
    Halted = $false; FocusRefused = $false; HaltReason = ''; CurrentOp = ''; InputOk = $false
}

# Which packages may hold the focus while the robot sends input, per op. The package under test is always allowed, for every op; this table
# lists the system surfaces that ONE op may meet besides it (the notification shade for the notification op, the system file picker and the
# permission dialog for pick, the permission dialog and the share chooser for key, which sends BACK only there). Nothing else is allowed anywhere.
$script:PaFocusAllow = [ordered]@{
    'notification' = @('com.android.systemui')
    'pick'         = @('com.android.documentsui', 'com.google.android.documentsui', 'com.sec.android.app.myfiles', 'com.google.android.permissioncontroller', 'com.android.permissioncontroller')
    'key'          = @('com.google.android.permissioncontroller', 'com.android.permissioncontroller', 'com.android.intentresolver', 'com.samsung.android.app.sharelive')
}

# The shapes the free adb op may run (the whole argument list joined with single spaces must match, case sensitive). {PKG} is the package the
# session targets. RO marks the read-only shapes: the only ones a session on the owner package could ever use (the op itself refuses that
# session first, see Invoke-PaOpAdb). Nothing that installs, removes, clears, writes, types or taps is on the list.
$script:PaAdbShapes = @(
    @{ Re = '^devices\z'; RO = $true },
    @{ Re = '^logcat -d( -v (brief|threadtime|time|long))?( -s [A-Za-z0-9_.]+:[VDIWEF])?\z'; RO = $true },
    @{ Re = '^logcat -c\z'; RO = $false },
    @{ Re = '^shell dumpsys (package|meminfo|gfxinfo) {PKG}( framestats)?\z'; RO = $true },
    @{ Re = '^shell dumpsys gfxinfo {PKG} reset\z'; RO = $false },
    @{ Re = '^shell dumpsys (alarm|window)\z'; RO = $true },
    @{ Re = '^shell pidof {PKG}\z'; RO = $true },
    @{ Re = '^shell wm size\z'; RO = $true },
    @{ Re = '^shell getprop [A-Za-z0-9_.]+\z'; RO = $true },
    @{ Re = '^shell cmd statusbar (expand-notifications|collapse)\z'; RO = $false },
    @{ Re = '^shell am start -n {PKG}/[A-Za-z0-9_.$]+\z'; RO = $false },
    @{ Re = '^shell am (force-stop|kill) {PKG}\z'; RO = $false }
)

# ============================================================================================================
# 1. small helpers
# ============================================================================================================

function Get-PaProp($Obj, [string]$Name, $Default = $null) {
    if ($null -eq $Obj) { return $Default }
    if ($Obj -is [System.Collections.IDictionary]) {
        if ($Obj.Contains($Name)) { return $Obj[$Name] }
        return $Default
    }
    $p = $Obj.PSObject.Properties[$Name]
    if ($null -ne $p) { return $p.Value }
    return $Default
}

function Test-PaHas($Obj, [string]$Name) {
    if ($null -eq $Obj) { return $false }
    if ($Obj -is [System.Collections.IDictionary]) { return $Obj.Contains($Name) }
    return ($null -ne $Obj.PSObject.Properties[$Name])
}

function Get-PaPropNames($Obj) {
    if ($null -eq $Obj) { return @() }
    if ($Obj -is [System.Collections.IDictionary]) { return @($Obj.Keys | ForEach-Object { [string]$_ }) }
    return @($Obj.PSObject.Properties | ForEach-Object { $_.Name })
}

function Quote-PaArg([string]$a) {
    if ($a -eq '') { return '""' }
    if ($a -cmatch '[\s"]') { return '"' + ($a -replace '"', '\"') + '"' }
    return $a
}

function New-PaDir([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { New-Item -ItemType Directory -Path $Path -Force | Out-Null }
}

function Write-PaTextFile([string]$Path, [string]$Text) {
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function Read-PaTextFile([string]$Path) {
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Read-PaJsonFile([string]$Path) {
    $t = Read-PaTextFile $Path
    if ($t.Length -gt 0 -and [int][char]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return (ConvertFrom-Json -InputObject $t)
}

function Write-PaJsonFile([string]$Path, $Obj, [int]$Depth = 12) {
    Write-PaTextFile $Path ((ConvertTo-Json -InputObject $Obj -Depth $Depth) + "`n")
}

function Get-PaRepoRoot { return (Resolve-Path -LiteralPath (Join-Path $script:PaLibDir '..')).Path }

function Format-PaMs([double]$v) { return ([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, '{0:0.0}', $v)) }

function Get-PaSha1Hex([string]$Text) {
    $sha = [System.Security.Cryptography.SHA1]::Create()
    try { $bytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Text)) } finally { $sha.Dispose() }
    return (($bytes | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Stop-Pa([string]$Message, [int]$Code = 2) {
    Write-Host ''
    Write-Host ('ERROR: ' + $Message) -ForegroundColor Red
    exit $Code
}

# ============================================================================================================
# 2. adb
# ============================================================================================================

function Initialize-PaProc {
    if ('PaProc' -as [type]) { return }
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

# Runs a program: object with ExitCode, Out, Err, Text, TimedOut. Never uses 2>&1 (PS 5.1 would turn stderr lines into errors).
function Invoke-PaNative([string]$Exe, [string[]]$ArgList, [int]$TimeoutMs = 30000) {
    Initialize-PaProc
    $line = (($ArgList | ForEach-Object { Quote-PaArg $_ }) -join ' ')
    $o = ''; $e = ''; $t = $false
    $code = [PaProc]::Run($Exe, $line, (Get-Location).Path, $TimeoutMs, [ref]$o, [ref]$e, [ref]$t)
    if ($t) { $e += "`n[timed out after " + [int]($TimeoutMs / 1000) + ' s]' }
    $o = $o -replace "`r", ''
    $e = $e -replace "`r", ''
    return New-Object psobject -Property @{ ExitCode = $code; Out = $o; Err = $e; Text = ($o + $e); TimedOut = $t }
}

function Find-PaAdb([string]$Hint = '') {
    if ($Hint -ne '' -and (Test-Path -LiteralPath $Hint)) { return (Resolve-Path -LiteralPath $Hint).Path }
    $c = Get-Command adb -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    $roots = @()
    if ($env:ANDROID_HOME) { $roots += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $roots += $env:ANDROID_SDK_ROOT }
    if ($env:LOCALAPPDATA) { $roots += (Join-Path (Join-Path $env:LOCALAPPDATA 'Android') 'Sdk') }
    foreach ($r in $roots) {
        foreach ($n in @('adb.exe', 'adb')) {
            $p = Join-Path (Join-Path $r 'platform-tools') $n
            if (Test-Path -LiteralPath $p) { return $p }
        }
    }
    return ''
}

# True when the arguments are a device shell command that injects input (tap, swipe, key, text, raw events, monkey). Only Invoke-PaInput may send one.
function Test-PaAdbInputCommand([string[]]$ArgList) {
    $i = [Array]::IndexOf($ArgList, 'shell')
    if ($i -lt 0) { return $false }
    $cmd = (@($ArgList | Select-Object -Skip ($i + 1)) -join ' ')
    return ($cmd -cmatch "(^|[;&|(]\s*|\bsh\s+-c\s+['""])\s*(input|monkey|sendevent)(\s|$)")
}

function Invoke-PaAdb([string[]]$ArgList, [int]$TimeoutMs = 30000) {
    if ((Test-PaAdbInputCommand $ArgList) -and -not [bool]$script:Pa.InputOk) {
        throw 'refused: an input command may only be sent by Invoke-PaInput (it checks the focus first)'
    }
    $a = @()
    if ($script:Pa.Serial -ne '') { $a += @('-s', $script:Pa.Serial) }
    $a += $ArgList
    return (Invoke-PaNative $script:Pa.Adb $a $TimeoutMs)
}

# One device command, ONE argument (single quotes inside are the device shell's).
function Invoke-PaShell([string]$Command, [int]$TimeoutMs = 30000) {
    return (Invoke-PaAdb @('shell', $Command) $TimeoutMs)
}

# A device command inside the app's own sandbox (works on debuggable builds only). Read-only use needs no guard; anything that writes or
# deletes goes through one of the guarded functions of section 3.
function Invoke-PaRunAs([string]$Command, [int]$TimeoutMs = 30000) {
    $inner = $Command -replace "'", "'\''"
    return (Invoke-PaShell ("run-as {0} sh -c '{1}'" -f $script:Pa.Package, $inner) $TimeoutMs)
}

function Get-PaDevices {
    $r = Invoke-PaNative $script:Pa.Adb @('devices') 20000
    $list = @()
    foreach ($l in ($r.Out -csplit "`n")) {
        if ($l -cmatch '^(\S+)\s+device\b') { $list += $Matches[1] }
    }
    return $list
}

# Chooses the phone: -Serial, else the only one. Returns '' when fine, else the reason.
function Select-PaDevice([string]$Serial) {
    $devs = @(Get-PaDevices)
    if ($Serial -ne '') {
        if ($devs -notcontains $Serial) { return 'the phone named by -Serial is not connected' }
        $script:Pa.Serial = $Serial
        return ''
    }
    if ($devs.Count -eq 0) { return 'no phone is connected (adb devices lists none)' }
    if ($devs.Count -gt 1) { return 'more than one phone is connected; pass -Serial' }
    $script:Pa.Serial = $devs[0]
    return ''
}

function Test-PaPackageInstalled([string]$Package) {
    $r = Invoke-PaShell ("pm path {0}" -f $Package)
    return ($r.Out -cmatch 'package:')
}

# Empty string when the screen is on and unlocked, else the reason.
function Test-PaScreenReady {
    $p = Invoke-PaShell 'dumpsys power'
    if ($p.Out -cnotmatch 'mWakefulness=Awake') { return 'the screen is off; wake and unlock the phone' }
    $w = Invoke-PaShell 'dumpsys window'
    if ($w.Out -cmatch '(isKeyguardShowing=true|mShowingLockscreen=true|isStatusBarKeyguard=true|keyguardShowing=true|mDreamingLockscreen=true)') {
        return 'the phone is locked; unlock it first'
    }
    return ''
}

function Get-PaScreenSize {
    if ($script:Pa.ScreenW -gt 0) { return @($script:Pa.ScreenW, $script:Pa.ScreenH) }
    $r = Invoke-PaShell 'wm size'
    $m = [regex]::Matches($r.Out, '(\d+)x(\d+)')
    if ($m.Count -gt 0) {
        $last = $m[$m.Count - 1]
        $script:Pa.ScreenW = [int]$last.Groups[1].Value
        $script:Pa.ScreenH = [int]$last.Groups[2].Value
    } else { $script:Pa.ScreenW = 1080; $script:Pa.ScreenH = 2340 }
    return @($script:Pa.ScreenW, $script:Pa.ScreenH)
}

function Get-PaVersionName([string]$Package) {
    $r = Invoke-PaShell ("dumpsys package {0}" -f $Package) 40000
    $m = [regex]::Match($r.Out, 'versionName=(\S+)')
    if ($m.Success) { return $m.Groups[1].Value }
    return 'unknown'
}

function Get-PaDeviceStamp {
    $r = Invoke-PaShell "date '+%Y-%m-%d %H:%M:%S'"
    return $r.Out.Trim()
}

# ---- app.log (read only) -----------------------------------------------------------------------------------
function Get-PaAppLog {
    $r = Invoke-PaRunAs 'cat files/logs/app.log.1 files/logs/app.log 2>/dev/null' 40000
    return $r.Out
}

# Lines of the log whose timestamp (device time, "yyyy-MM-dd HH:mm:ss") is not before $Stamp.
function Select-PaLogSince([string]$LogText, [string]$Stamp) {
    $keep = New-Object System.Collections.ArrayList
    foreach ($l in ($LogText -csplit "`n")) {
        if ($l.Length -ge 19 -and $l -cmatch '^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d') {
            if ($Stamp -eq '' -or [string]::CompareOrdinal($l.Substring(0, 19), $Stamp) -ge 0) { [void]$keep.Add($l) }
        }
    }
    return @($keep)
}

# The variant of the running build, from the last start line of app.log ('' when unreadable).
function Get-PaVariantFromLog([string]$LogText) {
    $variant = ''
    foreach ($m in [regex]::Matches($LogText, 'started Agiri build \S+ \([^)]*\) variant=(\S+)')) { $variant = $m.Groups[1].Value }
    return $variant
}

# 'on', 'off' or '' (unknown): the last line of app.log that says practice=on|off.
function Get-PaPracticeFromLog([string]$LogText) {
    $state = ''
    foreach ($m in [regex]::Matches($LogText, 'practice=(on|off)')) { $state = $m.Groups[1].Value }
    return $state
}

# The last I/State label of the CURRENT process (lines after the last "log opened" line), or ''.
function Get-PaStateFromLog([string]$LogText) {
    $lines = $LogText -csplit "`n"
    $start = 0
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -cmatch 'I/App: log opened') { $start = $i } }
    $state = ''
    for ($i = $start; $i -lt $lines.Count; $i++) {
        $m = [regex]::Match($lines[$i], ' I/State: (.+)$')
        if ($m.Success) { $state = $m.Groups[1].Value.Trim() }
    }
    return $state
}

# The route key of the last " I/Nav: top=<route>" line of app.log since the scenario started ('' when there is none).
function Get-PaNavTopLast {
    $lines = @(Select-PaLogSince (Get-PaAppLog) $script:Pa.StartStamp | Where-Object { $_ -cmatch ' I/Nav: top=' })
    if ($lines.Count -eq 0) { return '' }
    $m = [regex]::Match($lines[$lines.Count - 1], ' I/Nav: top=(\S+)')
    if ($m.Success) { return $m.Groups[1].Value }
    return ''
}

# Remembers the id of the chat that is on screen now (from the navigator's log line) in the scenario variable $Name ({{var:Name}}).
function Set-PaCapturedChat([string]$Name) {
    if ($Name -cnotmatch '^[A-Za-z0-9_]+$') { throw ("captureChat: bad variable name '{0}'" -f $Name) }
    $deadline = (Get-Date).AddSeconds(6)
    $top = ''
    while ($true) {
        $top = Get-PaNavTopLast
        $m = [regex]::Match($top, '^chat:([A-Za-z0-9_-]+)$')
        if ($m.Success) {
            $script:Pa.Vars[$Name] = $m.Groups[1].Value
            if ($null -eq $script:Pa.CapturedVars) { $script:Pa.CapturedVars = New-Object System.Collections.ArrayList }
            [void]$script:Pa.CapturedVars.Add($Name)
            return
        }
        if ((Get-Date) -gt $deadline) { throw ("captureChat: the navigator did not report a chat route (last top='{0}')" -f $top) }
        Start-Sleep -Milliseconds 400
    }
}

function Get-PaLatestChat {
    $log = Get-PaAppLog
    $id = ''
    foreach ($m in [regex]::Matches($log, 'I/Seed: chat (\S+)')) { $id = $m.Groups[1].Value }
    if ($id -ne '') { return $id }
    $r = Invoke-PaRunAs 'cat files/chat/conversations.json 2>/dev/null' 20000
    if ($r.Out.Trim() -eq '') { return '' }
    try {
        # ChatJson writes {"v":..,"conversations":[...]}
        $doc = ConvertFrom-Json -InputObject $r.Out
        $all = @(Get-PaProp $doc 'conversations' @())
        $best = ''; $bestAt = -1.0
        foreach ($c in $all) {
            $at = [double](Get-PaProp $c 'updatedAt' 0)
            if ($at -gt $bestAt) { $bestAt = $at; $best = [string](Get-PaProp $c 'id' '') }
        }
        return $best
    } catch { return '' }
}

# ============================================================================================================
# 3. SAFETY: the guard and the only place that changes system settings
# ============================================================================================================

# Throws unless the target is the test twin AND the newest start line of its app.log says variant=twin.
# The FIRST statement of every function that wipes, writes, seeds, kills a guest, installs, picks or runs a free adb call.
function Assert-PaTwinTarget([string]$Package) {
    if ($Package -ne $PaTwinPackage) {
        throw ("refused: this step may only run on the test twin ({0}); the target is {1}" -f $PaTwinPackage, $Package)
    }
    $r = Invoke-PaShell ("run-as {0} sh -c 'cat files/logs/app.log.1 files/logs/app.log 2>/dev/null'" -f $Package) 40000
    $variant = Get-PaVariantFromLog $r.Out
    if ($variant -ne 'twin') {
        throw ("refused: the newest start line of app.log says variant='{0}', not twin (or the log cannot be read)" -f $variant)
    }
}

function Test-PaSystemSettingsAllowed { return ((-not $script:Pa.Owner) -or $script:Pa.AllowSystemSettings) }

function Get-PaScalesBackupPath { return (Join-Path (Join-Path (Join-Path (Get-PaRepoRoot) 'dist') 'phone-test') 'system-settings-backup.json') }

# The ONLY function that runs "settings put". Before the first change it records the four current values in
# dist/phone-test/system-settings-backup.json (unless that file exists); -Restore puts them back and deletes the file.
# Keys: font_scale (system), animator_duration_scale, transition_animation_scale, window_animation_scale (global).
function Set-PaSystemScales {
    param([hashtable]$Values = @{}, [switch]$Restore)
    $ns = [ordered]@{ font_scale = 'system'; animator_duration_scale = 'global'; transition_animation_scale = 'global'; window_animation_scale = 'global' }
    $backup = Get-PaScalesBackupPath
    $inv = [System.Globalization.CultureInfo]::InvariantCulture
    if ($Restore) {
        if (-not (Test-Path -LiteralPath $backup)) { return }
        $saved = Read-PaJsonFile $backup
        $bad = New-Object System.Collections.ArrayList
        foreach ($k in $ns.Keys) {
            $v = [string](Get-PaProp $saved $k '1.0')
            if ($v -cnotmatch '^\d+(\.\d+)?$') { $v = '1.0' }
            $put = Invoke-PaShell ("settings put {0} {1} {2}" -f $ns[$k], $k, $v)
            $ok = ((-not $put.TimedOut) -and $put.ExitCode -eq 0)
            if ($ok) {
                # read it back: the backup is deleted only when every value is really on the phone again
                $back = Invoke-PaShell ("settings get {0} {1}" -f $ns[$k], $k)
                $got = $back.Out.Trim()
                $want = [double]::Parse($v, $inv)
                $num = 0.0
                $ok = ((-not $back.TimedOut) -and $back.ExitCode -eq 0 -and [double]::TryParse($got, [System.Globalization.NumberStyles]::Float, $inv, [ref]$num) -and [Math]::Abs($num - $want) -lt 0.0001)
            }
            if (-not $ok) { [void]$bad.Add($k) }
        }
        if ($bad.Count -gt 0) {
            throw ("system scales restore incomplete ({0}); the backup is kept at {1}" -f ($bad -join ', '), $backup)
        }
        Remove-Item -LiteralPath $backup -Force
        $script:Pa.ScalesTouched = $false
        return
    }
    if (-not (Test-PaSystemSettingsAllowed)) { throw 'refused: changing system settings on the owner package needs -AllowSystemSettings' }
    if (-not (Test-Path -LiteralPath $backup)) {
        $cur = [ordered]@{}
        foreach ($k in $ns.Keys) {
            $gr = Invoke-PaShell ("settings get {0} {1}" -f $ns[$k], $k)
            $g = $gr.Out.Trim()
            if ($gr.TimedOut -or $gr.ExitCode -ne 0) { throw ("cannot read the current value of {0} (adb failed); nothing was changed" -f $k) }
            if ($g -ceq 'null') { $g = '1.0' }   # the key was never set: the default is 1.0
            if ($g -cnotmatch '^\d+(\.\d+)?$') { throw ("cannot read the current value of {0} (got '{1}'); nothing was changed" -f $k, $g) }
            $cur[$k] = $g
        }
        Write-PaJsonFile $backup $cur
    }
    foreach ($k in $Values.Keys) {
        if (-not $ns.Contains([string]$k)) { throw ("unknown system scale '{0}'" -f $k) }
        $v = [string]$Values[$k]
        if ($v -cnotmatch '^\d+(\.\d+)?$') { throw ("bad value '{0}' for {1}" -f $v, $k) }
        Invoke-PaShell ("settings put {0} {1} {2}" -f $ns[[string]$k], $k, $v) | Out-Null
    }
    $script:Pa.ScalesTouched = $true
}

# Functional runs: no animation (the Home scene would keep uiautomator from going idle).
function Set-PaFunctionalScales { Set-PaSystemScales @{ animator_duration_scale = '0'; transition_animation_scale = '0'; window_animation_scale = '0' } }
function Set-PaPerfScales { Set-PaSystemScales @{ animator_duration_scale = '1'; transition_animation_scale = '1'; window_animation_scale = '1'; font_scale = '1.0' } }

# ---- the owner's package: alarm guard and practice switch --------------------------------------------------
# True when an alarm of the package is due within $Minutes. An alarm listing that cannot be parsed counts as due.
function Test-PaAlarmDue([string]$DumpText, [string]$Package, [int]$Minutes = 30) {
    # a real phone always lists system alarms and the clock: a text without both is empty or in a format this tool does not know
    if ($DumpText -cnotmatch 'nowELAPSED=\d+' -or $DumpText -cnotmatch 'Alarm\{') { return $true }
    $mine = @([regex]::Matches($DumpText, 'Alarm\{[^}]*\}') | Where-Object { $_.Value.Contains($Package) })
    if ($mine.Count -eq 0) { return $false }
    $now = [regex]::Match($DumpText, 'nowELAPSED=(\d+)')
    if (-not $now.Success) { return $true }
    $nowMs = [double]$now.Groups[1].Value
    foreach ($m in $mine) {
        $w = [regex]::Match($m.Value, 'whenElapsed (\d+)')
        if (-not $w.Success) { return $true }
        if (([double]$w.Groups[1].Value - $nowMs) -lt ($Minutes * 60000)) { return $true }
    }
    return $false
}

function Test-PaOwnerAlarmGuard {
    $r = Invoke-PaShell 'dumpsys alarm' 60000
    if ($r.TimedOut -or $r.ExitCode -ne 0) { return $true }
    return (Test-PaAlarmDue $r.Out $script:Pa.Package 30)
}

# May this text be typed on the owner's package? Plain words, LONG (with a count) and PAUSE only.
function Test-PaOwnerTypeAllowed([string]$Tag, [string]$Text) {
    if ($Tag -eq 'terminal.input') { return ($Text -ceq 'hermes --version') }
    foreach ($w in ($Text -csplit '[^A-Za-z_]+')) {
        if ($w -cmatch '^[A-Z_]{3,}$' -and ($script:PaKeywords -ccontains $w) -and $w -ne 'LONG' -and $w -ne 'PAUSE') { return $false }
    }
    return $true
}

# ---- the focus guard: the only way input reaches the phone ----------------------------------------------------
# An earlier experiment sent blind taps into a third-party app after the app under test had crashed. So no input leaves this script
# without Invoke-PaInput, and Invoke-PaInput sends nothing unless the window that has the focus belongs to the package under test (or, for
# the few ops named in $script:PaFocusAllow, to a system surface that op meets). Invoke-PaAdb refuses an input command from anywhere else, and
# the self test greps every tool script for the spelling of one outside Invoke-PaInput.

# The system packages the op may meet besides the package under test (see the table at the top of this file).
function Get-PaFocusExtra([string]$Op) {
    if ($script:PaFocusAllow.Contains($Op)) { return @($script:PaFocusAllow[$Op]) }
    return @()
}

# The text of the lines "mCurrentFocus=..." and "mFocusedApp=..." of `dumpsys window` into facts: Windows (distinct, non-null: Hash, Name, Package
# - the package is known only when the window name is "<package>/<class>"), Apps (the packages of the focused activities), Seen (a line was there).
function Get-PaFocusFacts([string]$Text) {
    $windows = New-Object System.Collections.ArrayList
    $apps = New-Object System.Collections.ArrayList
    $seen = $false
    foreach ($raw in ($Text -csplit "`n")) {
        $l = $raw.Trim()
        $m = [regex]::Match($l, '^mCurrentFocus=Window\{([0-9A-Fa-f]+) u\d+ (.*)\}$')
        if ($m.Success) {
            $seen = $true
            $name = $m.Groups[2].Value.Trim()
            $pkg = ''
            $c = [regex]::Match($name, '^([A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+)/\S+$')
            if ($c.Success) { $pkg = $c.Groups[1].Value }
            # a window without a title is named by the package the system recorded for it (a dialog or sheet of the app looks like this)
            elseif ($name -cmatch '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$') { $pkg = $name }
            elseif ($name -ceq 'NotificationShade') { $pkg = 'com.android.systemui' }   # only SystemUI can create this window type
            $dup = $false
            foreach ($w in $windows) { if ($w.Hash -ceq $m.Groups[1].Value) { $dup = $true } }
            if (-not $dup) { [void]$windows.Add([pscustomobject]@{ Hash = $m.Groups[1].Value; Name = $name; Package = $pkg }) }
            continue
        }
        if ($l -cmatch '^mCurrentFocus=') { $seen = $true; continue }
        $a = [regex]::Match($l, '^mFocusedApp=ActivityRecord\{[0-9A-Fa-f]+ u\d+ ([A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+)/')
        if ($a.Success) { $seen = $true; if (-not $apps.Contains($a.Groups[1].Value)) { [void]$apps.Add($a.Groups[1].Value) } }
        elseif ($l -cmatch '^mFocusedApp=') { $seen = $true }
    }
    return [pscustomobject]@{ Windows = @($windows); Apps = @($apps); Seen = $seen }
}

# The package that owns the window with this hash, read from the "Window #n Window{<hash> ...}:" block of `dumpsys window windows` ("package=<name>"),
# or '' when the block or the line is not there.
function Get-PaWindowPackage([string]$WindowsText, [string]$Hash) {
    if ($Hash -cnotmatch '^[0-9A-Fa-f]+$') { return '' }
    $start = [regex]::Match($WindowsText, 'Window #\d+ Window\{' + $Hash + ' [^\r\n]*\}:')
    if (-not $start.Success) { return '' }
    $rest = $WindowsText.Substring($start.Index + $start.Length)
    $next = [regex]::Match($rest, '\n\s*Window #\d+ ')
    if ($next.Success) { $rest = $rest.Substring(0, $next.Index) }
    $p = [regex]::Match($rest, '\bpackage=([A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*)')
    if ($p.Success) { return $p.Groups[1].Value }
    return ''
}

# Pure. Is the focus in these facts acceptable? Result: Ok, Kind (ok | none | lookup | foreign), Why.
#   none    no window has the focus (or the lines are unreadable): the caller may read again, a transition can show it for a moment
#   lookup  a window whose package is not in its name: the caller reads `dumpsys window windows` and asks again with its text in $WindowsText
#   foreign the focus is somewhere else (the message names the window)
# Every focused window must belong to the package under test or to $Extra; when one belongs to the package under test, every focused activity
# must too (mFocusedApp is the second source).
function Test-PaFocusFacts($Facts, [string]$Package, [string[]]$Extra = @(), [string]$WindowsText = '') {
    if (-not $Facts.Seen -or @($Facts.Windows).Count -eq 0) {
        return [pscustomobject]@{ Ok = $false; Kind = 'none'; Why = 'no window has the focus (mCurrentFocus is null or cannot be read)' }
    }
    $ownWindow = $false
    foreach ($w in @($Facts.Windows)) {
        $pkg = $w.Package
        if ($pkg -eq '' -and $WindowsText -eq '') {
            return [pscustomobject]@{ Ok = $false; Kind = 'lookup'; Why = ('the package of the window ' + $w.Name + ' is not in its name') }
        }
        if ($pkg -eq '') { $pkg = Get-PaWindowPackage $WindowsText $w.Hash }
        if ($pkg -eq '') {
            return [pscustomobject]@{ Ok = $false; Kind = 'foreign'; Why = ('focus is the window ''' + $w.Name + ''' whose package cannot be read, not ' + $Package) }
        }
        if ($pkg -ceq $Package) { $ownWindow = $true; continue }
        if (@($Extra) -ccontains $pkg) { continue }
        return [pscustomobject]@{ Ok = $false; Kind = 'foreign'; Why = ('focus is ' + $pkg + ' (window ''' + $w.Name + '''), not ' + $Package) }
    }
    if ($ownWindow) {
        foreach ($a in @($Facts.Apps)) {
            if ($a -cne $Package) {
                return [pscustomobject]@{ Ok = $false; Kind = 'foreign'; Why = ('the focused window is ' + $Package + ' but the focused activity is ' + $a) }
            }
        }
    }
    return [pscustomobject]@{ Ok = $true; Kind = 'ok'; Why = '' }
}

# Reads the focus from the phone (two read-only dumpsys calls at most) and judges it. Sends nothing.
function Get-PaFocusVerdict([string[]]$Extra = @()) {
    $r = Invoke-PaShell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'" 30000
    if ($r.TimedOut -or $r.ExitCode -ne 0) {
        return [pscustomobject]@{ Ok = $false; Kind = 'foreign'; Why = 'the focus could not be read from the phone (dumpsys failed, timed out or listed no focus line)' }
    }
    $facts = Get-PaFocusFacts $r.Out
    $v = Test-PaFocusFacts $facts $script:Pa.Package $Extra ''
    if ($v.Kind -eq 'lookup') {
        $w = Invoke-PaShell 'dumpsys window windows' 60000
        $v = Test-PaFocusFacts $facts $script:Pa.Package $Extra $w.Out
        if ($v.Kind -eq 'lookup') { $v = [pscustomobject]@{ Ok = $false; Kind = 'foreign'; Why = $v.Why } }
    }
    return $v
}

# Stops all input: remembers why, takes a picture, throws. The runners see FocusRefused and end the run.
function Stop-PaForFocus([string]$Why) {
    $script:Pa.Halted = $true
    $script:Pa.FocusRefused = $true
    $script:Pa.HaltReason = $Why
    $shot = ''
    try { $shot = Save-PaShot 'focus-refused' } catch { }
    $tail = ''
    if ($shot -ne '') { $tail = (' (shot ' + $shot + ')') }
    throw ('refused: focus: ' + $Why + '; no input was sent' + $tail)
}

# Throws unless the focus is where input may go. -AppOnly ignores the system surfaces of the op. A stopped run stays stopped
# until a link (Invoke-PaOpLink) has brought the package under test to the front again.
function Assert-PaFocus([string]$Op = '', [switch]$AppOnly) {
    if ($script:Pa.Halted) {
        throw ('refused: focus: input is stopped (' + $script:Pa.HaltReason + '); a link step has to bring the app to the front first')
    }
    if ($Op -eq '') { $Op = [string]$script:Pa.CurrentOp }
    $extra = @()
    if (-not $AppOnly) { $extra = @(Get-PaFocusExtra $Op) }
    $v = $null
    for ($i = 0; $i -lt 4; $i++) {
        $v = Get-PaFocusVerdict $extra
        if ($v.Kind -ne 'none') { break }
        Start-Sleep -Milliseconds 350
    }
    if ($v.Ok) { return }
    Stop-PaForFocus $v.Why
}

# THE ONLY function that spells a device input command. Kind: tap, swipe, text or keyevent. Checks the focus first, every time.
function Invoke-PaInput([string]$Kind, [string]$InputArgs, [switch]$AppOnly) {
    if (@('tap', 'swipe', 'text', 'keyevent') -cnotcontains $Kind) { throw ("input: unknown kind '{0}'" -f $Kind) }
    Assert-PaFocus '' -AppOnly:$AppOnly
    $script:Pa.InputOk = $true
    try { Invoke-PaShell ("input {0} {1}" -f $Kind, $InputArgs) | Out-Null } finally { $script:Pa.InputOk = $false }
}

# After a link: when the run was stopped by the focus guard, the stop ends only if the package under test really is in front now.
function Clear-PaHaltAfterLink {
    if (-not $script:Pa.Halted) { return }
    Start-Sleep -Milliseconds 900
    $v = Get-PaFocusVerdict @()
    if ($v.Ok) { $script:Pa.Halted = $false; $script:Pa.HaltReason = '' }
}

# For the runners' finally blocks: the app under test is brought to the front by an explicit link (no input), so that practice mode can still
# be switched off. Does nothing when the run was not stopped.
function Restore-PaForeground {
    if (-not $script:Pa.Halted) { return }
    try { Invoke-PaOpLink ([pscustomobject]@{ intent = [pscustomobject]@{ action = 'android.intent.action.MAIN' } }) }
    catch { Write-Host ('NOTE the app could not be brought to the front: ' + $_.Exception.Message) }
}

# A free-form adb call: '' when the argument list is one of the allowed shapes for this package, else the reason. Pure.
function Test-PaAdbShape([string[]]$ArgList, [string]$Package, [bool]$OwnerSession) {
    $joined = (@($ArgList) -join ' ')
    if ($joined.Trim() -eq '') { return 'no arguments' }
    if ($joined -cmatch '[\x00-\x1F\x7F`$;|&<>(){}\[\]\\!*?''"]') { return 'the arguments contain a character the allow list never needs ($ ` ; | & < > ( ) { } [ ] \ ! * ? quotes or a control character)' }
    $ownerToken = '(^|[^.\w])' + [regex]::Escape($PaOwnerPackage) + '(\z|[^.\w])'
    if ((-not (Test-PaOwnerPackage $Package)) -and $joined -cmatch $ownerToken) { return 'an argument names the owner package while the session targets another one' }
    foreach ($s in $script:PaAdbShapes) {
        if ($OwnerSession -and -not $s.RO) { continue }
        $re = $s.Re.Replace('{PKG}', [regex]::Escape($Package))
        if ($joined -cmatch $re) { return '' }
    }
    if ($OwnerSession) { return 'this shape is not on the read-only list (sessions on the owner package get nothing else)' }
    return 'this argument list is not one of the shapes on the allow list (tools/phone-lib.ps1, PaAdbShapes)'
}

# The package an APK declares (aapt2 dump packagename). Throws when aapt2 or the answer is missing: nothing is installed on a guess.
function Get-PaApkPackage([string]$Apk) {
    $roots = @()
    if ($env:ANDROID_HOME) { $roots += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $roots += $env:ANDROID_SDK_ROOT }
    if ($env:LOCALAPPDATA) { $roots += (Join-Path (Join-Path $env:LOCALAPPDATA 'Android') 'Sdk') }
    $exe = ''
    foreach ($r in $roots) {
        $bt = Join-Path $r 'build-tools'
        if (-not (Test-Path -LiteralPath $bt)) { continue }
        foreach ($d in @(Get-ChildItem -LiteralPath $bt -Directory | Sort-Object Name -Descending)) {
            foreach ($n in @('aapt2.exe', 'aapt2')) {
                $p = Join-Path $d.FullName $n
                if ($exe -eq '' -and (Test-Path -LiteralPath $p)) { $exe = $p }
            }
        }
    }
    if ($exe -eq '') { throw 'refused: install: aapt2 was not found, so the package of the APK cannot be read' }
    $r = Invoke-PaNative $exe @('dump', 'packagename', $Apk) 120000
    $name = $r.Out.Trim()
    if ($r.ExitCode -ne 0 -or $name -cnotmatch '^[A-Za-z0-9_.]+$') { throw ('refused: install: the package of the APK cannot be read (' + $r.Text.Trim() + ')') }
    return $name
}

# Throws unless the package is the test twin. The install op calls it with the package of the APK it is about to install.
function Assert-PaInstallablePackage([string]$Package) {
    if ($Package -cne $PaTwinPackage) {
        throw ("refused: install: the APK is for '{0}', not for the test twin ({1}); the owner package is never installed from a scenario" -f $Package, $PaTwinPackage)
    }
}

# ============================================================================================================
# 4. the UI dump
# ============================================================================================================

function ConvertFrom-PaBounds([string]$s) {
    $m = [regex]::Match($s, '\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]')
    if (-not $m.Success) { return @(0, 0, 0, 0) }
    return @([int]$m.Groups[1].Value, [int]$m.Groups[2].Value, [int]$m.Groups[3].Value, [int]$m.Groups[4].Value)
}

function Add-PaDumpNode($Xml, $Parent, $All) {
    $b = ConvertFrom-PaBounds ([string]$Xml.GetAttribute('bounds'))
    $rid = [string]$Xml.GetAttribute('resource-id')
    $slash = $rid.LastIndexOf('/')
    if ($slash -ge 0) { $rid = $rid.Substring($slash + 1) }
    $node = [pscustomobject]@{
        Tag = $rid
        Text = [string]$Xml.GetAttribute('text')
        Desc = [string]$Xml.GetAttribute('content-desc')
        Class = [string]$Xml.GetAttribute('class')
        Package = [string]$Xml.GetAttribute('package')
        L = $b[0]; T = $b[1]; R = $b[2]; B = $b[3]
        Clickable = ($Xml.GetAttribute('clickable') -eq 'true')
        Checkable = ($Xml.GetAttribute('checkable') -eq 'true')
        LongClickable = ($Xml.GetAttribute('long-clickable') -eq 'true')
        Checked = ($Xml.GetAttribute('checked') -eq 'true')
        Enabled = ($Xml.GetAttribute('enabled') -ne 'false')
        Selected = ($Xml.GetAttribute('selected') -eq 'true')
        Scrollable = ($Xml.GetAttribute('scrollable') -eq 'true')
        Parent = $Parent
        Children = (New-Object System.Collections.ArrayList)
    }
    [void]$All.Add($node)
    if ($null -ne $Parent) { [void]$Parent.Children.Add($node) }
    foreach ($c in $Xml.ChildNodes) {
        if ($c.NodeType -eq [System.Xml.XmlNodeType]::Element -and $c.Name -eq 'node') { Add-PaDumpNode $c $node $All }
    }
}

# The text of a uiautomator dump into a dump object: Nodes (document order), Roots, Sig, Name.
function ConvertFrom-PaDumpXml([string]$XmlText) {
    $doc = New-Object System.Xml.XmlDocument
    $doc.LoadXml($XmlText)
    $all = New-Object System.Collections.ArrayList
    foreach ($c in $doc.DocumentElement.ChildNodes) {
        if ($c.NodeType -eq [System.Xml.XmlNodeType]::Element -and $c.Name -eq 'node') { Add-PaDumpNode $c $null $all }
    }
    $dump = [pscustomobject]@{ Nodes = $all; Sig = ''; Name = '' }
    $tags = @($all | Where-Object { $_.Tag -ne '' } | ForEach-Object { $_.Tag } | Sort-Object -Unique)
    if ($tags.Count -eq 0) {
        $tags = @($all | Where-Object { $_.Clickable } | ForEach-Object { $_.Text + '|' + $_.Desc } | Sort-Object -Unique)
    }
    $dump.Sig = (Get-PaSha1Hex ($tags -join "`n")).Substring(0, 8)
    $name = ''
    foreach ($t in $tags) { if ($t -clike 'screen.*') { $name = $t; break } }
    if ($name -eq '') { $name = 'sig' }
    $dump.Name = $name
    return $dump
}

function Test-PaSelectorOne($Node, $Sel, [string]$ScopePackage) {
    if ($Node.Package -ne $ScopePackage) { return $false }
    $v = Get-PaProp $Sel 'tag' $null;           if ($null -ne $v -and $Node.Tag -cne [string]$v) { return $false }
    $v = Get-PaProp $Sel 'tagRe' $null;         if ($null -ne $v -and -not ($Node.Tag -cmatch [string]$v)) { return $false }
    $v = Get-PaProp $Sel 'text' $null;          if ($null -ne $v -and $Node.Text -cne [string]$v) { return $false }
    $v = Get-PaProp $Sel 'desc' $null;          if ($null -ne $v -and $Node.Desc -cne [string]$v) { return $false }
    $v = Get-PaProp $Sel 'textContains' $null;  if ($null -ne $v -and -not $Node.Text.Contains([string]$v)) { return $false }
    $v = Get-PaProp $Sel 'descContains' $null;  if ($null -ne $v -and -not $Node.Desc.Contains([string]$v)) { return $false }
    $v = Get-PaProp $Sel 're' $null
    if ($null -ne $v) {
        if (-not ($Node.Text -cmatch [string]$v) -and -not ($Node.Desc -cmatch [string]$v)) { return $false }
    }
    return $true
}

function Test-PaSelectorHasField($Sel) {
    foreach ($f in @('tag', 'tagRe', 'text', 'desc', 'textContains', 'descContains', 're', 'any')) { if (Test-PaHas $Sel $f) { return $true } }
    return $false
}

function Get-PaScopePackage($Sel) {
    $scope = [string](Get-PaProp $Sel 'scope' 'app')
    if ($scope -eq 'systemui') { return 'com.android.systemui' }
    return $script:Pa.Package
}

# All nodes the selector matches (document order). For `any`: the matches of the first alternative that has some. `idx` picks one.
function Find-PaNodes($Dump, $Sel) {
    if ($null -eq $Dump) { return @() }
    $alts = @(Get-PaProp $Sel 'any' @())
    $scopePkg = Get-PaScopePackage $Sel
    $result = @()
    if ($alts.Count -gt 0) {
        foreach ($a in $alts) {
            $pkg = $scopePkg
            if (Test-PaHas $a 'scope') { $pkg = Get-PaScopePackage $a }
            $hit = @($Dump.Nodes | Where-Object { Test-PaSelectorOne $_ $a $pkg })
            if ($hit.Count -gt 0) { $result = $hit; break }
        }
    } else {
        $result = @($Dump.Nodes | Where-Object { Test-PaSelectorOne $_ $Sel $scopePkg })
    }
    $idx = Get-PaProp $Sel 'idx' $null
    if ($null -ne $idx) {
        $i = [int]$idx
        if ($i -lt 0) { $i = $result.Count + $i }   # -1 is the last match
        if ($i -ge 0 -and $i -lt $result.Count) { return @($result[$i]) }
        return @()
    }
    return $result
}

function Find-PaNode($Dump, $Sel) {
    $m = @(Find-PaNodes $Dump $Sel)
    if ($m.Count -eq 0) { return $null }
    return $m[0]
}

# checked / enabled / selected of the node, or of its first checkable descendant.
function Get-PaNodeFlag($Node, [string]$Flag) {
    $n = $Node
    if (-not $n.Checkable) {
        $queue = New-Object System.Collections.Queue
        foreach ($c in $n.Children) { $queue.Enqueue($c) }
        while ($queue.Count -gt 0) {
            $c = $queue.Dequeue()
            if ($c.Checkable) { $n = $c; break }
            foreach ($d in $c.Children) { $queue.Enqueue($d) }
        }
    }
    switch ($Flag) {
        'checked' { return [bool]$n.Checked }
        'enabled' { return [bool]$n.Enabled }
        'selected' { return [bool]$n.Selected }
    }
    return $false
}

function Get-PaNodeCenter($Node) { return @([int](($Node.L + $Node.R) / 2), [int](($Node.T + $Node.B) / 2)) }

# ---- automatic checks ---------------------------------------------------------------------------------------
# The spoken label of a node: its text, else its description, else the first non-empty text or description of a descendant (4 levels).
function Get-PaSpokenLabel($Node, [int]$Depth = 4) {
    if ($Node.Text.Trim() -ne '') { return $Node.Text }
    if ($Node.Desc.Trim() -ne '') { return $Node.Desc }
    if ($Depth -le 0) { return '' }
    foreach ($c in $Node.Children) {
        $l = Get-PaSpokenLabel $c ($Depth - 1)
        if ($l -ne '') { return $l }
    }
    return ''
}

function Get-PaNodeId($Node) {
    if ($Node.Tag -ne '') { return $Node.Tag }
    return ('{0}@[{1},{2}][{3},{4}]' -f $Node.Class, $Node.L, $Node.T, $Node.R, $Node.B)
}

# Interactive nodes of the app without a spoken label. $Pkg is the app's package; $W,$H the screen.
function Get-PaLabelFindings($Dump, [string]$Pkg, [int]$W, [int]$H) {
    $out = @()
    # A row that is scrolled half under the top bar or the tab bar shows only a sliver in the dump and its labelled children are cut away:
    # a node whose vertical extent crosses the lower edge of the top bar or the upper edge of the tab bar is skipped (found on the real phone, Wave 0 proof).
    $topBand = 0; $navTop = $H
    foreach ($n in $Dump.Nodes) {
        if ($n.Package -ne $Pkg) { continue }
        if ($n.Tag -eq 'top.back' -or $n.Tag -eq 'top.title') { if ($n.B -gt $topBand) { $topBand = $n.B } }
        elseif ($n.Tag -eq 'nav.bar') { if ($n.T -lt $navTop) { $navTop = $n.T } }
    }
    foreach ($n in $Dump.Nodes) {
        if ($n.Package -ne $Pkg) { continue }
        if (-not ($n.Clickable -or $n.Checkable -or $n.LongClickable)) { continue }
        if (($n.R - $n.L) -le 4 -or ($n.B - $n.T) -le 4) { continue }
        if ($n.R -le 0 -or $n.B -le 0 -or $n.L -ge $W -or $n.T -ge $H) { continue }
        if ($topBand -gt 0 -and $n.T -lt $topBand -and $n.B -gt $topBand + 2) { continue }
        if ($navTop -lt $H -and $n.T -lt $navTop -and $n.B -gt $navTop + 2) { continue }
        # the same for a chip of a sideways scroller that peeks in at the left or right edge of the screen: a narrow sliver whose label is off screen
        if (($n.R - $n.L) -lt 100 -and ($n.R -ge $W - 1 -or $n.L -le 1)) { continue }
        if ((Get-PaSpokenLabel $n 4) -eq '') { $out += [pscustomobject]@{ Id = (Get-PaNodeId $n); Text = '' } }
    }
    return $out
}

# Text nodes that leave the screen by more than 2 px in a chain without a scrolling ancestor.
function Get-PaCutFindings($Dump, [string]$Pkg, [int]$W, [int]$H) {
    $out = @()
    foreach ($n in $Dump.Nodes) {
        if ($n.Package -ne $Pkg) { continue }
        if ($n.Text.Trim() -eq '') { continue }
        $scrolls = $false
        $p = $n.Parent
        while ($null -ne $p) { if ($p.Scrollable) { $scrolls = $true; break }; $p = $p.Parent }
        if ($scrolls) { continue }
        if (($n.R - $n.L) -le 0 -or ($n.B - $n.T) -le 0) { continue }
        if ($n.L -lt -2 -or $n.T -lt -2 -or $n.R -gt ($W + 2) -or $n.B -gt ($H + 2)) {
            $out += [pscustomobject]@{ Id = (Get-PaNodeId $n); Text = $n.Text }
        }
    }
    return $out
}

function Test-PaAllowed($AllowList, [string]$ScreenName, [string]$ScreenSig, [string]$Id) {
    foreach ($a in @($AllowList)) {
        $s = [string](Get-PaProp $a 'screen' '*')
        $i = [string](Get-PaProp $a 'id' '*')
        $screenOk = ($s -eq '*' -or $s -eq $ScreenName -or $s -eq $ScreenSig)
        $idOk = ($i -eq '*' -or $i -eq $Id)
        if ($screenOk -and $idOk) { return $true }
    }
    return $false
}

function Read-PaAllowFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    $j = Read-PaJsonFile $Path
    return @(Get-PaProp $j 'allow' @())
}

# Called with every dump the robot takes. A screen signature is checked once per run.
function Register-PaDumpChecks($Dump) {
    if (-not $script:Pa.ChecksOn -or $null -eq $Dump) { return }
    $key = $Dump.Sig + '|' + [string]$script:Pa.CutMode
    if ($script:Pa.SeenScreens.ContainsKey($key)) { return }
    $script:Pa.SeenScreens[$key] = $true
    $wh = Get-PaScreenSize
    $label = $Dump.Name + '#' + $Dump.Sig
    if (-not $script:Pa.NoA11y -and -not $script:Pa.CutMode) {
        foreach ($f in @(Get-PaLabelFindings $Dump $script:Pa.Package $wh[0] $wh[1])) {
            if (Test-PaAllowed $script:Pa.A11yAllow $Dump.Name $Dump.Sig $f.Id) { continue }
            [void]$script:Pa.A11y.Add(('A11Y screen={0} id={1} no spoken label' -f $label, $f.Id))
        }
    }
    if (-not $script:Pa.NoCut -and $script:Pa.CutMode) {
        foreach ($f in @(Get-PaCutFindings $Dump $script:Pa.Package $wh[0] $wh[1])) {
            if (Test-PaAllowed $script:Pa.CutAllow $Dump.Name $Dump.Sig $f.Id) { continue }
            [void]$script:Pa.Cut.Add(('CUT screen={0} "{1}"' -f $label, $f.Text))
        }
    }
}

# A fresh dump (retried 3 times: uiautomator fails while the UI is not idle). $null when none could be taken.
function Get-PaDump([int]$Retries = 3) {
    $hostFile = Join-Path (Get-PaTempDir) 'dump.xml'
    for ($i = 0; $i -lt $Retries; $i++) {
        $r = Invoke-PaShell 'uiautomator dump /data/local/tmp/pa-dump.xml' 25000
        if ($r.Text -cmatch 'dumped to') {
            Invoke-PaAdb @('pull', '/data/local/tmp/pa-dump.xml', $hostFile) 25000 | Out-Null
            if (Test-Path -LiteralPath $hostFile) {
                try {
                    $text = Read-PaTextFile $hostFile
                    $d = ConvertFrom-PaDumpXml $text
                    $script:Pa.LastDump = $d
                    $script:Pa.LastDumpText = $text
                    Register-PaDumpChecks $d
                    return $d
                } catch { }
            }
        }
        Start-Sleep -Milliseconds 400
    }
    return $null
}

function Get-PaTempDir {
    $d = Join-Path (Join-Path (Join-Path (Get-PaRepoRoot) 'dist') 'phone-test') 'tmp'
    New-PaDir $d
    return $d
}

# ============================================================================================================
# 5. the step engine
# ============================================================================================================

# {{pkg}} {{latestChat}} {{now}} {{var:name}} in every string of a step. Unknown names stay as they are.
function Expand-PaStep($Step) {
    $json = ConvertTo-Json -InputObject $Step -Depth 20 -Compress
    if ($json -cnotmatch '\{\{') { return $Step }
    $sb = New-Object System.Text.StringBuilder
    $pos = 0
    foreach ($m in [regex]::Matches($json, '\{\{([A-Za-z]+)(?::([^{}]+))?\}\}')) {
        [void]$sb.Append($json.Substring($pos, $m.Index - $pos))
        $pos = $m.Index + $m.Length
        $name = $m.Groups[1].Value
        $val = $null
        switch ($name) {
            'pkg' { $val = $script:Pa.Package }
            'latestChat' { $val = (Get-PaLatestChat) }
            'now' { $val = (Get-Date).ToString('yyyyMMdd-HHmmss') }
            'var' {
                $k = $m.Groups[2].Value
                if ($script:Pa.Vars.ContainsKey($k)) { $val = [string]$script:Pa.Vars[$k] }
            }
        }
        if ($null -eq $val) { [void]$sb.Append($m.Value) }
        else {
            $esc = ConvertTo-Json -InputObject ([string]$val) -Compress
            [void]$sb.Append($esc.Substring(1, $esc.Length - 2))
        }
    }
    [void]$sb.Append($json.Substring($pos))
    return (ConvertFrom-Json -InputObject $sb.ToString())
}

function Wait-PaSleep([int]$Ms) { Start-Sleep -Milliseconds $Ms }

# Called in every wait loop; phone-perf installs a hook here that polls the frame statistics.
function Invoke-PaHook { if ($null -ne $script:Pa.Hook) { & $script:Pa.Hook } }

# Waits for a selector to match (or, with -Gone, to stop matching). Result: Ok, Dump, Node.
function Wait-PaNode($Sel, [int]$TimeoutSec = 10, [switch]$Gone) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $last = $null
    while ($true) {
        Invoke-PaHook
        $d = Get-PaDump
        if ($null -ne $d) {
            $last = $d
            $n = Find-PaNode $d $Sel
            if ($Gone) {
                if ($null -eq $n) { return [pscustomobject]@{ Ok = $true; Dump = $d; Node = $null } }
            } elseif ($null -ne $n) {
                return [pscustomobject]@{ Ok = $true; Dump = $d; Node = $n }
            }
        }
        if ((Get-Date) -gt $deadline) { break }
        Start-Sleep -Milliseconds 500
    }
    return [pscustomobject]@{ Ok = $false; Dump = $last; Node = $null }
}

function Format-PaSelector($Sel) {
    $parts = @()
    foreach ($f in @('tag', 'tagRe', 'text', 'desc', 'textContains', 'descContains', 're', 'idx')) {
        if (Test-PaHas $Sel $f) { $parts += ('{0}={1}' -f $f, (Get-PaProp $Sel $f)) }
    }
    $alts = @(Get-PaProp $Sel 'any' @())
    if ($alts.Count -gt 0) { $parts += ('any=[' + ((@($alts | ForEach-Object { Format-PaSelector $_ })) -join ' | ') + ']') }
    if ($parts.Count -eq 0) { return '(no selector)' }
    return ($parts -join ' ')
}

function Send-PaTap([int]$X, [int]$Y) { Invoke-PaInput 'tap' ("{0} {1}" -f $X, $Y) }

function Send-PaKey([string]$Name) {
    $codes = @{ HOME = 3; BACK = 4; ENTER = 66; DEL = 67; TAB = 61; APP_SWITCH = 187 }
    $k = $Name.ToUpperInvariant()
    if (-not $codes.ContainsKey($k)) { throw ("unknown key '{0}'" -f $Name) }
    # in the key op a system surface (permission dialog, share chooser) may hold the focus for BACK only
    $appOnly = (($k -ne 'BACK') -and ([string]$script:Pa.CurrentOp -ceq 'key'))
    Invoke-PaInput 'keyevent' ([string]$codes[$k]) -AppOnly:$appOnly
}

function Send-PaText([string]$Text) {
    if ($Text -cmatch "[^\x20-\x7E]") { throw 'type: the text must be ASCII (the typing command cannot type other characters)' }
    if ($Text.Contains("'")) { throw "type: the text cannot contain a single quote" }
    $enc = $Text -replace ' ', '%s'
    $i = 0
    while ($i -lt $enc.Length) {
        $len = [Math]::Min(40, $enc.Length - $i)
        # never cut a %s in the middle
        if ($enc[$i + $len - 1] -eq '%' -and ($i + $len) -lt $enc.Length) { $len++ }
        Invoke-PaInput 'text' ("'{0}'" -f $enc.Substring($i, $len))
        $i += $len
    }
}

function Save-PaShot([string]$Name) {
    $script:Pa.ShotNo = $script:Pa.ShotNo + 1
    $file = ('{0:00}-{1}.png' -f $script:Pa.ShotNo, ($Name -creplace '[^A-Za-z0-9._-]', '_'))
    $shotDir = [string]$script:Pa.ScenarioDir
    if ($shotDir -eq '') { $shotDir = Get-PaTempDir }
    $dest = Join-Path $shotDir $file
    Invoke-PaShell 'screencap -p /data/local/tmp/pa-shot.png' 30000 | Out-Null
    Invoke-PaAdb @('pull', '/data/local/tmp/pa-shot.png', $dest) 30000 | Out-Null
    Invoke-PaShell 'rm -f /data/local/tmp/pa-shot.png' | Out-Null
    return $file
}

function Save-PaFailureDump {
    if ([string]$script:Pa.LastDumpText -ne '') {
        $f = Join-Path $script:Pa.ScenarioDir ('dump-{0:00}.xml' -f $script:Pa.StepNo)
        Write-PaTextFile $f ([string]$script:Pa.LastDumpText)
        return (Split-Path -Leaf $f)
    }
    return ''
}

# ---- logcat -------------------------------------------------------------------------------------------------
function Update-PaLogcat {
    $r = Invoke-PaAdb @('logcat', '-d', '-v', 'threadtime') 60000
    $script:Pa.LogcatText += $r.Out
    Invoke-PaAdb @('logcat', '-c') 20000 | Out-Null
}

function Get-PaCrashLines([string]$Text, [string]$Package) {
    $found = @()
    $lines = $Text -csplit "`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $l = $lines[$i]
        if ($l.Contains('FATAL EXCEPTION')) {
            $win = ($lines[$i..([Math]::Min($i + 4, $lines.Count - 1))]) -join "`n"
            if ($win.Contains($Package)) { $found += $l.Trim() }
        } elseif ($l.Contains('Fatal signal')) {
            if ($l.Contains($Package)) { $found += $l.Trim() }
        } elseif ($l.Contains('ANR in ' + $Package)) {
            $found += $l.Trim()
        } elseif ($l -cmatch 'NoSuchMethodError|ClassNotFoundException') {
            if ($l.Contains($Package) -or $l.Contains('AndroidRuntime')) { $found += $l.Trim() }
        }
    }
    return $found
}

# ---- the ops ------------------------------------------------------------------------------------------------
# The token of the debug link: the content of files/debug-link.token of the app under test (32 hex characters made at its first start; another
# app cannot read it, the shell can on a debuggable build through run-as). Read-only. When the file is not there yet the app is started once
# with its normal launcher intent and the file is waited for.
function Get-PaDebugToken {
    $deadline = (Get-Date).AddSeconds(30)
    $started = $false
    while ($true) {
        $r = Invoke-PaShell ("run-as {0} sh -c 'cat files/debug-link.token 2>/dev/null'" -f $script:Pa.Package) 20000
        $t = $r.Out.Trim()
        if ($t -cmatch '^[0-9a-f]{16,64}$') { return $t }
        if (-not $started) {
            Invoke-PaShell ("am start -n {0}" -f (Get-PaComponent $script:Pa.Package $PaMainActivity)) 30000 | Out-Null
            $started = $true
        }
        if ((Get-Date) -gt $deadline) { throw 'link: files/debug-link.token of the app under test cannot be read (is this a build with test hooks?)' }
        Start-Sleep -Seconds 1
    }
}

function Invoke-PaOpLink($Step) {
    $comp = Get-PaComponent $script:Pa.Package $PaMainActivity
    $act = [string](Get-PaProp $Step 'action' '')
    $dbg = Get-PaProp $Step 'debug' $null
    $intent = Get-PaProp $Step 'intent' $null
    $line = ''
    if ($act -ne '') {
        $line = ("am start -a dev.pocketagent.app.{0} -n {1}" -f $act, $comp)
        $conv = [string](Get-PaProp $Step 'conv_id' '')
        if ($conv -ne '') {
            if ($conv -cnotmatch '^[A-Za-z0-9_-]+$') { throw 'link: bad conv_id' }
            $line += (" -d agiri://chat/{0} --es conv_id {1}" -f $conv, $conv)
        }
    } elseif ($null -ne $dbg) {
        $line = ("am start -a dev.pocketagent.app.DEBUG -n {0}" -f $comp)
        foreach ($k in (Get-PaPropNames $dbg)) {
            $v = [string](Get-PaProp $dbg $k '')
            if ($k -cnotmatch '^[a-z]+$') { throw ("link: bad extra name '{0}'" -f $k) }
            if ($k -ceq 'token') { throw 'link: the token extra is added by the robot, a scenario does not name it' }
            if ($v.Contains("'")) { throw 'link: a single quote in an extra' }
            $line += (" --es {0} '{1}'" -f $k, $v)
        }
        # the debug link is inert without the token only the shell can read (see DebugLinks.kt)
        $line += (" --es token '{0}'" -f (Get-PaDebugToken))
    } elseif ($null -ne $intent) {
        $ia = [string](Get-PaProp $intent 'action' 'android.intent.action.MAIN')
        if ($ia -cnotmatch '^[A-Za-z0-9_.]+$') { throw 'link: bad intent action' }
        $line = ("am start -a {0} -n {1}" -f $ia, $comp)
        $data = [string](Get-PaProp $intent 'data' '')
        if ($data -ne '') {
            if ($data -cnotmatch '^[A-Za-z0-9_.:/-]+$') { throw 'link: bad intent data' }
            $line += (" -d {0}" -f $data)
        }
    } else { throw 'link needs action, debug or intent' }
    $r = Invoke-PaShell $line 30000
    if ($r.Text -cmatch '(?m)^Error' -or $r.Text -cmatch 'Exception') { throw ("link failed: " + $r.Text.Trim()) }
    Start-Sleep -Milliseconds 400
    # a run stopped by the focus guard goes on only after a link has really put the app under test in front
    Clear-PaHaltAfterLink
}

function Invoke-PaOpTap($Step) {
    $t = [int](Get-PaProp $Step 'timeout' 10)
    $hold = [int](Get-PaProp $Step 'holdMs' 0)
    $key = ''
    if ([bool](Get-PaProp $Step 'cache' $false)) {
        # perf runs: the first tap finds the node with a dump, later taps reuse its position (a dump would disturb the frames)
        $key = Format-PaSelector $Step
        if ($script:Pa.TapCache.ContainsKey($key)) {
            $p = $script:Pa.TapCache[$key]
            Send-PaTap $p[0] $p[1]
            Start-Sleep -Milliseconds 150
            return
        }
    }
    $w = Wait-PaNode $Step $t
    if (-not $w.Ok) { throw ("tap: {0} not found after {1}s" -f (Format-PaSelector $Step), $t) }
    $c = Get-PaNodeCenter $w.Node
    if ($key -ne '') { $script:Pa.TapCache[$key] = $c }
    if ($hold -gt 0) { Invoke-PaInput 'swipe' ("{0} {1} {0} {1} {2}" -f $c[0], $c[1], $hold) }
    else { Send-PaTap $c[0] $c[1] }
    Start-Sleep -Milliseconds 300
}

function Invoke-PaOpType($Step) {
    $text = [string](Get-PaProp $Step 'text' '')
    # `text` is what is typed; the field is found by the other selector fields only
    $sel = New-Object psobject
    foreach ($f in @('tag', 'tagRe', 'desc', 'textContains', 'descContains', 're', 'idx', 'any', 'scope')) {
        if (Test-PaHas $Step $f) { Add-Member -InputObject $sel -NotePropertyName $f -NotePropertyValue (Get-PaProp $Step $f) }
    }
    $hasSel = (Test-PaSelectorHasField $sel)
    if ($hasSel) {
        $t = [int](Get-PaProp $Step 'timeout' 10)
        $w = Wait-PaNode $sel $t
        if (-not $w.Ok) { throw ("type: {0} not found after {1}s" -f (Format-PaSelector $sel), $t) }
        if ($script:Pa.Owner -and -not (Test-PaOwnerTypeAllowed $w.Node.Tag $text)) { throw 'refused: on the owner package only plain text, LONG and PAUSE may be typed (and hermes --version into the terminal)' }
        $c = Get-PaNodeCenter $w.Node
        Send-PaTap $c[0] $c[1]
        Start-Sleep -Milliseconds 300
    } elseif ($script:Pa.Owner -and -not (Test-PaOwnerTypeAllowed '' $text)) {
        throw 'refused: on the owner package only plain text, LONG and PAUSE may be typed'
    }
    if ([bool](Get-PaProp $Step 'clear' $false)) {
        Invoke-PaInput 'keyevent' '123'
        Invoke-PaInput 'keyevent' ((1..60 | ForEach-Object { '67' }) -join ' ')
    }
    Send-PaText $text
    if ([bool](Get-PaProp $Step 'submit' $false)) { Send-PaKey 'ENTER' }
}

# A state label shown in the interface (translated) as the English word the log uses, or the label itself when unknown.
function ConvertTo-PaEnglishState([string]$Label) {
    # the translated words are built from char codes so that this file stays pure ASCII (Windows PowerShell 5.1 reads it as ANSI)
    $trRunning = 'Running|' + [char]0x00C7 + 'al' + [char]0x0131 + [char]0x015F + [char]0x0131 + 'yor|En marche'
    $trStarting = 'Starting|Ba' + [char]0x015F + 'l' + [char]0x0131 + 'yor|D' + [char]0x00E9 + 'marrage'
    $trStopped = 'Stopped|Durdu|Arr' + [char]0x00EA + 't' + [char]0x00E9
    if ($Label -cmatch ('^(' + $trRunning + ')')) { return 'Running' }
    if ($Label -cmatch ('^(' + $trStarting + ')')) { return 'Starting' }
    if ($Label -cmatch ('^(' + $trStopped + ')')) { return 'Stopped' }
    return $Label
}

# The state of Hermes: from the (language independent) I/State line of app.log on every package; only when the log cannot be read
# does the owner package fall back to the visible, possibly translated, label of settings.runtime.state.
function Get-PaCurrentState {
    $fromLog = Get-PaStateFromLog (Get-PaAppLog)
    if ($fromLog -ne '') { return $fromLog }
    if ($script:Pa.Owner) {
        $d = Get-PaDump
        if ($null -eq $d) { return '' }
        $n = Find-PaNode $d ([pscustomobject]@{ tag = 'settings.runtime.state' })
        if ($null -eq $n) { return '' }
        return (ConvertTo-PaEnglishState $n.Text)
    }
    return ''
}

function Test-PaStateMatches([string]$Label, [string]$Want) {
    if ($Label -eq '') { return $false }
    return ($Label -eq $Want -or $Label.StartsWith($Want + '(') -or $Label.StartsWith($Want))
}

function Invoke-PaOpWait($Step) {
    $t = [int](Get-PaProp $Step 'timeout' 30)
    if (Test-PaHas $Step 'ms') {
        $left = [int]$Step.ms
        while ($left -gt 0) {
            $slice = [Math]::Min($left, 400)
            Start-Sleep -Milliseconds $slice
            $left -= $slice
            Invoke-PaHook
        }
        return
    }
    $deadline = (Get-Date).AddSeconds($t)
    if (Test-PaHas $Step 'state') {
        $want = [string]$Step.state
        while ($true) {
            Invoke-PaHook
            if (Test-PaStateMatches (Get-PaCurrentState) $want) { return }
            if ((Get-Date) -gt $deadline) { throw ("wait: state {0} not reached after {1}s (last: '{2}')" -f $want, $t, (Get-PaCurrentState)) }
            Start-Sleep -Milliseconds 500
        }
    }
    if (Test-PaHas $Step 'log') {
        $re = [string]$Step.log
        while ($true) {
            Invoke-PaHook
            $since = Select-PaLogSince (Get-PaAppLog) $script:Pa.StartStamp
            if (@($since | Where-Object { $_ -cmatch $re }).Count -gt 0) { return }
            if ((Get-Date) -gt $deadline) { throw ("wait: log /{0}/ not seen after {1}s" -f $re, $t) }
            Start-Sleep -Milliseconds 500
        }
    }
    $gone = [bool](Get-PaProp $Step 'gone' $false)
    $w = Wait-PaNode $Step $t -Gone:$gone
    $found = $w.Ok
    if (-not $found -and (Test-PaHas $Step 'navTop') -and -not $gone) {
        # the screen tag is not exported (one UI can drop it): the navigator's own log line says where we are
        if ((Get-PaNavTopLast) -ceq [string]$Step.navTop) { $found = $true }
    }
    if ($found -and -not $gone -and (Test-PaHas $Step 'captureChat')) { Set-PaCapturedChat ([string]$Step.captureChat) }
    if (-not $found) { throw ("wait: {0}{1} after {2}s" -f (Format-PaSelector $Step), $(if ($gone) { ' still there' } else { ' not found' }), $t) }
}

function Invoke-PaOpExpect($Step, [bool]$Negate) {
    $default = 8
    if ($Negate) { $default = 3 }
    $t = [int](Get-PaProp $Step 'timeout' $default)
    $deadline = (Get-Date).AddSeconds($t)
    if (Test-PaHas $Step 'log') {
        $re = [string]$Step.log
        while ($true) {
            $hit = @((Select-PaLogSince (Get-PaAppLog) $script:Pa.StartStamp) | Where-Object { $_ -cmatch $re }).Count
            if ($Negate) {
                if ($hit -gt 0) { throw ("expectNot: log /{0}/ appeared" -f $re) }
                if ((Get-Date) -gt $deadline) { return }
            } else {
                if ($hit -gt 0) { return }
                if ((Get-Date) -gt $deadline) { throw ("expect: log /{0}/ not seen after {1}s" -f $re, $t) }
            }
            Start-Sleep -Milliseconds 500
        }
    }
    if ((Test-PaHas $Step 'navIs') -or (Test-PaHas $Step 'chatHas')) { Invoke-PaOpExpectChat $Step $Negate $t; return }
    $lastReason = 'not found'
    while ($true) {
        Invoke-PaHook
        $d = Get-PaDump
        if ($null -ne $d) {
            $nodes = @(Find-PaNodes $d $Step)
            if ($Negate) {
                if ($nodes.Count -gt 0) { throw ("expectNot: {0} is present" -f (Format-PaSelector $Step)) }
            } else {
                $ok = ($nodes.Count -gt 0)
                if ($ok -and (Test-PaHas $Step 'count')) { $ok = ($nodes.Count -eq [int]$Step.count); if (-not $ok) { $lastReason = ('{0} matches, wanted {1}' -f $nodes.Count, $Step.count) } }
                if ($ok) {
                    foreach ($f in @('checked', 'enabled', 'selected')) {
                        if (Test-PaHas $Step $f) {
                            if ((Get-PaNodeFlag $nodes[0] $f) -ne [bool]$Step.$f) { $ok = $false; $lastReason = ('{0} is not {1}' -f $f, $Step.$f) }
                        }
                    }
                }
                if ($ok) { return }
            }
        }
        if ((Get-Date) -gt $deadline) {
            if ($Negate) { return }
            throw ("expect: {0} {1} after {2}s" -f (Format-PaSelector $Step), $lastReason, $t)
        }
        Start-Sleep -Milliseconds 500
    }
}

# expect / expectNot with navIs (the route key the navigator logged last, e.g. chat:<id>) and/or chatHas (plain text that is in the chat
# file of the chat on screen; read from the app's own files, nothing is written). Both must hold.
function Invoke-PaOpExpectChat($Step, [bool]$Negate, [int]$T) {
    $deadline = (Get-Date).AddSeconds($T)
    $route = [string](Get-PaProp $Step 'navIs' '')
    $needle = [string](Get-PaProp $Step 'chatHas' '')
    if ($needle -ne '' -and $needle -cnotmatch '^[A-Za-z0-9 _.\[\]-]+$') { throw 'expect: chatHas takes letters, digits, space and _ . [ ] - only' }
    $why = ''
    while ($true) {
        $top = Get-PaNavTopLast
        $ok = $true
        if ($route -ne '' -and $top -cne $route) { $ok = $false; $why = ("the route is '{0}', not '{1}'" -f $top, $route) }
        if ($ok -and $needle -ne '') {
            $m = [regex]::Match($top, '^chat:([A-Za-z0-9_-]+)$')
            if (-not $m.Success) { $ok = $false; $why = 'no chat is on screen' }
            else {
                $r = Invoke-PaRunAs ("grep -c -F '{0}' files/chat/messages/{1}.json 2>/dev/null" -f $needle, $m.Groups[1].Value)
                if ($r.Out.Trim() -cnotmatch '^[1-9]') { $ok = $false; $why = 'the text is not in the file of this chat' }
            }
        }
        if ($ok) {
            if ($Negate) { throw 'expectNot: the route and the chat text are both there' }
            return
        }
        if ((Get-Date) -gt $deadline) {
            if ($Negate) { return }
            throw ("expect: {0}" -f $why)
        }
        Start-Sleep -Milliseconds 500
    }
}

function Invoke-PaOpNoCrash {
    Update-PaLogcat
    $bad = @(Get-PaCrashLines $script:Pa.LogcatText $script:Pa.Package)
    if ($bad.Count -gt 0) { throw ("noCrash: " + $bad[0]) }
    if (-not $script:Pa.Owner) {
        $since = Select-PaLogSince (Get-PaAppLog) $script:Pa.StartStamp
        $c = @($since | Where-Object { $_ -cmatch ' E/CRASH:' })
        if ($c.Count -gt 0) { throw ("noCrash: app.log: " + $c[0]) }
    }
}

function Invoke-PaOpKill {
    $before = (Invoke-PaShell ("pidof {0}" -f $script:Pa.Package)).Out.Trim()
    Send-PaKey 'HOME'
    Start-Sleep -Seconds 1
    Invoke-PaShell ("am kill {0}" -f $script:Pa.Package) | Out-Null
    Start-Sleep -Milliseconds 800
    $after = (Invoke-PaShell ("pidof {0}" -f $script:Pa.Package)).Out.Trim()
    if ($before -ne '' -and $before -eq $after) { throw ("kill: the process {0} is still alive (am kill only ends a background process)" -f $before) }
}

function Invoke-PaOpForceStop { Invoke-PaShell ("am force-stop {0}" -f $script:Pa.Package) | Out-Null; Start-Sleep -Milliseconds 600 }

function Invoke-PaOpSwipe($Step) {
    $wh = Get-PaScreenSize
    $dir = [string](Get-PaProp $Step 'dir' 'up')
    $ms = [int](Get-PaProp $Step 'ms' 300)
    $times = [int](Get-PaProp $Step 'times' 1)
    $x = [int]($wh[0] / 2)
    $y1 = [int]($wh[1] * 0.72); $y2 = [int]($wh[1] * 0.30)
    if ($dir -eq 'down') { $tmp = $y1; $y1 = $y2; $y2 = $tmp } elseif ($dir -ne 'up') { throw 'swipe: dir is up or down' }
    for ($i = 0; $i -lt $times; $i++) {
        Invoke-PaInput 'swipe' ("{0} {1} {2} {3} {4}" -f $x, $y1, $x, $y2, $ms)
        Invoke-PaHook
        Start-Sleep -Milliseconds 150
    }
}

function Invoke-PaOpScale($Step) {
    if (-not (Test-PaSystemSettingsAllowed)) { throw 'scale: refused on the owner package without -AllowSystemSettings' }
    $vals = @{}
    if (Test-PaHas $Step 'font') { $vals['font_scale'] = ([string]$Step.font) }
    if (Test-PaHas $Step 'animations') {
        $a = [string]$Step.animations
        $vals['animator_duration_scale'] = $a; $vals['transition_animation_scale'] = $a; $vals['window_animation_scale'] = $a
    }
    if ($vals.Count -eq 0) { throw 'scale needs font or animations' }
    Set-PaSystemScales $vals
    $script:Pa.ScenarioScaled = $true
}

function Invoke-PaOpA11y {
    $d = Get-PaDump
    if ($null -eq $d) { throw 'a11y: no UI dump could be taken' }
    $wh = Get-PaScreenSize
    $label = $d.Name + '#' + $d.Sig
    $n = 0
    if (-not $script:Pa.NoA11y) {
        foreach ($f in @(Get-PaLabelFindings $d $script:Pa.Package $wh[0] $wh[1])) {
            if (Test-PaAllowed $script:Pa.A11yAllow $d.Name $d.Sig $f.Id) { continue }
            $line = ('A11Y screen={0} id={1} no spoken label' -f $label, $f.Id)
            if (-not $script:Pa.A11y.Contains($line)) { [void]$script:Pa.A11y.Add($line); $n++ }
        }
    }
    if (-not $script:Pa.NoCut -and $script:Pa.CutMode) {
        foreach ($f in @(Get-PaCutFindings $d $script:Pa.Package $wh[0] $wh[1])) {
            if (Test-PaAllowed $script:Pa.CutAllow $d.Name $d.Sig $f.Id) { continue }
            $line = ('CUT screen={0} "{1}"' -f $label, $f.Text)
            if (-not $script:Pa.Cut.Contains($line)) { [void]$script:Pa.Cut.Add($line) }
        }
        # texts the app itself reported as ellipsized (CutProbe, tag AgiriCut): they stay inside their bounds, so only the log shows them
        foreach ($t in @(Get-PaCutProbeTexts)) {
            if (Test-PaAllowed $script:Pa.CutAllow $d.Name $d.Sig $t) { continue }
            $line = ('CUT screen={0} "{1}"' -f $label, $t)
            if (-not $script:Pa.Cut.Contains($line)) { [void]$script:Pa.Cut.Add($line) }
        }
    }
}

# The distinct texts of AgiriCut warnings in a logcat text of the format "-v brief" ("W/AgiriCut( 123): text").
function ConvertFrom-PaCutProbe([string]$Text) {
    $seen = New-Object System.Collections.ArrayList
    foreach ($l in ($Text -csplit "`n")) {
        $m = [regex]::Match($l, 'AgiriCut\s*(?:\(\s*\d+\))?\s*:\s*(.*)$')
        if (-not $m.Success) { continue }
        $t = $m.Groups[1].Value.Trim()
        if ($t -ne '' -and -not $seen.Contains($t)) { [void]$seen.Add($t) }
    }
    return @($seen)
}

# Reads the AgiriCut warnings since the last read and then drains logcat into the run's log (so the next screen only sees its own lines).
function Get-PaCutProbeTexts {
    $r = Invoke-PaAdb @('logcat', '-d', '-v', 'brief', '-s', 'AgiriCut:W') 30000
    $texts = @(ConvertFrom-PaCutProbe $r.Out)
    try { Update-PaLogcat } catch { }
    return $texts
}

# Keeps the dump of the screen in the scenario folder and runs tools/strings-check.ps1 -ScanDump on it: plain English left on a screen in the
# stretched language is a leak (exit 4 of the checker). The allow list is tools/scenarios/leak-allow.txt.
function Invoke-PaOpScanDump($Step) {
    $d = Get-PaDump
    if ($null -eq $d) { throw 'scanDump: no UI dump could be taken' }
    $f = Join-Path $script:Pa.ScenarioDir ('scan-{0:00}.xml' -f $script:Pa.StepNo)
    Write-PaTextFile $f ([string]$script:Pa.LastDumpText)
    $checker = Join-Path (Join-Path (Get-PaRepoRoot) 'tools') 'strings-check.ps1'
    if (-not (Test-Path -LiteralPath $checker)) { throw 'scanDump: tools/strings-check.ps1 is missing' }
    $hostExe = (Get-Process -Id $PID).Path
    $argv = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $checker, '-ScanDump', $f)
    $allow = Join-Path (Get-PaScenarioRoot) 'leak-allow.txt'
    if (Test-Path -LiteralPath $allow) { $argv += @('-Allow', $allow) }
    $r = Invoke-PaNative $hostExe $argv 120000
    if ($r.ExitCode -eq 0) { return }
    $leaks = @(($r.Out -csplit "`n") | Where-Object { $_.StartsWith('LEAK ') })
    if ($leaks.Count -gt 0) {
        foreach ($l in $leaks) { [void]$script:Pa.Leaks.Add(('LEAK screen={0}#{1} "{2}"' -f $d.Name, $d.Sig, $l.Substring(5))) }
        return
    }
    throw ("scanDump: strings-check exited {0}: {1}" -f $r.ExitCode, $r.Text.Trim())
}

function Invoke-PaOpExpectColor($Step) {
    $t = [int](Get-PaProp $Step 'timeout' 8)
    $w = Wait-PaNode $Step $t
    if (-not $w.Ok) { throw ("expectColor: {0} not found" -f (Format-PaSelector $Step)) }
    $file = Save-PaShot 'color'
    $path = Join-Path $script:Pa.ScenarioDir $file
    Add-Type -AssemblyName System.Drawing
    $bmp = New-Object System.Drawing.Bitmap($path)
    try {
        $dom = [string](Get-PaProp $Step 'dominant' 'red')
        $min = [int](Get-PaProp $Step 'min' 20)
        $n = $w.Node
        $count = 0
        for ($y = [Math]::Max(0, $n.T); $y -lt [Math]::Min($bmp.Height, $n.B); $y += 2) {
            for ($x = [Math]::Max(0, $n.L); $x -lt [Math]::Min($bmp.Width, $n.R); $x += 2) {
                $c = $bmp.GetPixel($x, $y)
                switch ($dom) {
                    'red' { if ($c.R -gt 150 -and $c.G -lt 110 -and $c.B -lt 110) { $count++ } }
                    'green' { if ($c.G -gt 130 -and $c.R -lt 110 -and $c.B -lt 130) { $count++ } }
                    'blue' { if ($c.B -gt 150 -and $c.R -lt 110 -and $c.G -lt 130) { $count++ } }
                    default { throw 'expectColor: dominant is red, green or blue' }
                }
            }
        }
        if ($count -lt $min) { throw ("expectColor: only {0} {1} pixels (wanted {2})" -f $count, $dom, $min) }
    } finally { $bmp.Dispose() }
}

function Invoke-PaOpNotification($Step) {
    $t = [int](Get-PaProp $Step 'timeout' 30)
    $deadline = (Get-Date).AddSeconds($t)
    $ex = Get-PaProp $Step 'expect' $null
    if ($null -ne $ex) {
        $tc = [string](Get-PaProp $ex 'titleContains' '')
        $xc = [string](Get-PaProp $ex 'textContains' '')
        while ($true) {
            $r = Invoke-PaShell 'dumpsys notification --noredact' 60000
            $mine = @()
            foreach ($blk in ($r.Out -csplit '(?m)^\s*NotificationRecord')) { if ($blk -cmatch [regex]::Escape($script:Pa.Package)) { $mine += $blk } }
            $ok = $false
            foreach ($b in $mine) {
                if (($tc -eq '' -or $b.Contains($tc)) -and ($xc -eq '' -or $b.Contains($xc))) { $ok = $true; break }
            }
            if ($ok) { break }
            if ((Get-Date) -gt $deadline) { throw ("notification: none with title '{0}' text '{1}' after {2}s" -f $tc, $xc, $t) }
            Start-Sleep -Seconds 2
        }
    }
    $label = [string](Get-PaProp $Step 'tap' '')
    $reply = [string](Get-PaProp $Step 'reply' '')
    $open = [bool](Get-PaProp $Step 'open' $false)
    if ($label -ne '' -or $reply -ne '' -or $open) {
        Invoke-PaShell 'cmd statusbar expand-notifications' | Out-Null
        Start-Sleep -Seconds 1
        $sel = [pscustomobject]@{ scope = 'systemui'; textContains = $label }
        if ($label -eq '') { $sel = [pscustomobject]@{ scope = 'systemui'; textContains = 'Agir' } }
        if ($reply -ne '') { $sel = [pscustomobject]@{ scope = 'systemui'; re = '^(Reply|Yan.tla|R.pondre)' } }
        $w = Wait-PaNode $sel 10
        if (-not $w.Ok) { Invoke-PaShell 'cmd statusbar collapse' | Out-Null; throw 'notification: the shade has no matching entry (this op is fragile on One UI)' }
        $c = Get-PaNodeCenter $w.Node
        Send-PaTap $c[0] $c[1]
        Start-Sleep -Milliseconds 600
        if ($reply -ne '') { Send-PaText $reply; Send-PaKey 'ENTER'; Start-Sleep -Milliseconds 600 }
        if (-not $open -and $reply -eq '') { Invoke-PaShell 'cmd statusbar collapse' | Out-Null }
    }
    if ([bool](Get-PaProp $Step 'clear' $false)) { Invoke-PaShell 'cmd statusbar collapse' | Out-Null }
}

# ---- guarded ops (twin only; the first statement of each is Assert-PaTwinTarget) ---------------------------
function Invoke-PaOpWipe($Step) {
    Assert-PaTwinTarget $script:Pa.Package
    Invoke-PaShell ("pm clear {0}" -f $PaTwinPackage) 60000 | Out-Null
    Invoke-PaShell ("am start -n {0}" -f (Get-PaComponent $PaTwinPackage $PaMainActivity)) | Out-Null
    $deadline = (Get-Date).AddSeconds(420)
    while ((Get-Date) -lt $deadline) {
        $r = Invoke-PaRunAs 'ls files/rootfs.stamp 2>/dev/null' 20000
        if ($r.Out -cmatch 'rootfs.stamp') { return }
        Start-Sleep -Seconds 5
    }
    throw 'wipe: the Linux image was not extracted again within 7 minutes'
}

function Invoke-PaOpPref($Step) {
    Assert-PaTwinTarget $script:Pa.Package
    $file = [string](Get-PaProp $Step 'file' ''); $key = [string](Get-PaProp $Step 'key' '')
    $type = [string](Get-PaProp $Step 'type' 'string'); $value = [string](Get-PaProp $Step 'value' '')
    if ($file -cnotmatch '^[A-Za-z0-9_.-]+$' -or $key -cnotmatch '^[A-Za-z0-9_.-]+$') { throw 'pref: bad file or key name' }
    if (@('boolean', 'int', 'long', 'float', 'string') -notcontains $type) { throw 'pref: type is boolean, int, long, float or string' }
    Invoke-PaOpForceStop
    $xmlText = (Invoke-PaRunAs ("cat shared_prefs/{0}.xml 2>/dev/null" -f $file) 20000).Out
    if ($xmlText.Trim() -eq '') { $xmlText = '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map />' }
    $doc = New-Object System.Xml.XmlDocument
    $doc.LoadXml($xmlText)
    $map = $doc.DocumentElement
    foreach ($old in @($map.ChildNodes | Where-Object { $_.NodeType -eq 'Element' -and $_.GetAttribute('name') -eq $key })) { [void]$map.RemoveChild($old) }
    $el = $doc.CreateElement($type)
    $el.SetAttribute('name', $key)
    if ($type -eq 'string') { $el.InnerText = $value } else { $el.SetAttribute('value', $value) }
    [void]$map.AppendChild($el)
    $tmp = Join-Path (Get-PaTempDir) 'pref.xml'
    Write-PaTextFile $tmp $doc.OuterXml
    Invoke-PaAdb @('push', $tmp, '/data/local/tmp/pa-pref.xml') 30000 | Out-Null
    Invoke-PaShell 'chmod 644 /data/local/tmp/pa-pref.xml' | Out-Null
    $r = Invoke-PaShell ("run-as {0} sh -c 'cp /data/local/tmp/pa-pref.xml shared_prefs/{1}.xml'" -f $PaTwinPackage, $file) 20000
    Invoke-PaShell 'rm -f /data/local/tmp/pa-pref.xml' | Out-Null
    if ($r.Text.Trim() -ne '') { throw ("pref: " + $r.Text.Trim()) }
}

function Invoke-PaOpSeed($Step) {
    Assert-PaTwinTarget $script:Pa.Package
    $spec = [string](Get-PaProp $Step 'spec' '')
    if ($spec -cnotmatch '^(chats:\d+|chat:\d+:\d+|routine:\d+|usage:\d+:(WARN|BLOCK|OFF)|clear)$') { throw ("seed: bad spec '{0}'" -f $spec) }
    $before = Get-Date
    Invoke-PaOpLink ([pscustomobject]@{ debug = [pscustomobject]@{ seed = $spec } })
    $deadline = $before.AddSeconds(60)
    $want = ' I/Seed: '
    while ((Get-Date) -lt $deadline) {
        $log = Select-PaLogSince (Get-PaAppLog) $script:Pa.StartStamp
        if (@($log | Where-Object { $_.Contains($want) }).Count -gt 0) { return }
        if ($spec -eq 'clear') { return }
        Start-Sleep -Milliseconds 700
    }
    throw ("seed: no 'I/Seed:' line in app.log within 60s for '{0}' (the link may have been rejected: the build must be the twin)" -f $spec)
}

function Invoke-PaOpKillGuest($Step = $null) {
    Assert-PaTwinTarget $script:Pa.Package
    $sh = 'n=0; for p in /proc/[0-9]*; do if tr "\0" "\n" < $p/environ 2>/dev/null | grep -q "^PA_IN_PROOT=1$"; then kill -9 ${p#/proc/} 2>/dev/null; n=$((n+1)); fi; done; echo killed=$n'
    $r = Invoke-PaRunAs $sh 30000
    if ($r.Out -cnotmatch 'killed=') { throw ("killGuest: " + $r.Text.Trim()) }
}

function Invoke-PaOpExpectFile($Step) {
    Assert-PaTwinTarget $script:Pa.Package
    $path = [string](Get-PaProp $Step 'path' '')
    if ($path -cnotmatch '^[A-Za-z0-9_./*-]+$' -or $path.Contains('..') -or $path.StartsWith('/')) { throw ("expectFile: bad path '{0}'" -f $path) }
    $glob = ($path -replace '\*\*/', '*/')
    $ls = Invoke-PaRunAs ("ls -d files/{0} 2>/dev/null" -f $glob) 20000
    $first = ($ls.Out -csplit "`n" | Where-Object { $_.Trim() -ne '' } | Select-Object -First 1)
    if ([bool](Get-PaProp $Step 'absent' $false)) {
        if ($null -ne $first) { throw ("expectFile: {0} exists" -f $path) }
        return
    }
    if ($null -eq $first) { throw ("expectFile: {0} does not exist" -f $path) }
    $needsText = (Test-PaHas $Step 'contains') -or (Test-PaHas $Step 'regex') -or (Test-PaHas $Step 'lines')
    if (-not $needsText) { return }
    $text = (Invoke-PaRunAs ("cat '{0}'" -f $first.Trim()) 30000).Out
    if (Test-PaHas $Step 'contains') {
        if (-not $text.Contains([string]$Step.contains)) { throw ("expectFile: {0} does not contain '{1}'" -f $path, $Step.contains) }
    }
    if (Test-PaHas $Step 'regex') {
        if ($text -cnotmatch [string]$Step.regex) { throw ("expectFile: {0} does not match /{1}/" -f $path, $Step.regex) }
    }
    if (Test-PaHas $Step 'lines') {
        $cfg = $Step.lines
        $re = [string](Get-PaProp $cfg 'match' '.')
        $n = @(($text -csplit "`n") | Where-Object { $_ -cmatch $re }).Count
        $min = [int](Get-PaProp $cfg 'min' 0); $max = [int](Get-PaProp $cfg 'max' 2147483647)
        if ($n -lt $min -or $n -gt $max) { throw ("expectFile: {0} has {1} lines matching /{2}/ (wanted {3}..{4})" -f $path, $n, $re, $min, $max) }
    }
}

function Invoke-PaOpRestore($Step) {
    Assert-PaTwinTarget $script:Pa.Package
    $from = [string](Get-PaProp $Step 'from' ''); $to = [string](Get-PaProp $Step 'to' '')
    if ($to -cnotmatch '^[A-Za-z0-9_./-]+$' -or $to.Contains('..') -or $to.StartsWith('/')) { throw 'restore: bad target path' }
    $src = Join-Path (Join-Path (Join-Path (Get-PaRepoRoot) 'dist') 'phone-test') $from
    if (-not (Test-Path -LiteralPath $src)) { throw ("restore: {0} does not exist under dist/phone-test" -f $from) }
    Invoke-PaOpForceStop
    Invoke-PaAdb @('push', $src, '/data/local/tmp/pa-restore.bin') 120000 | Out-Null
    Invoke-PaShell 'chmod 644 /data/local/tmp/pa-restore.bin' | Out-Null
    $r = Invoke-PaShell ("run-as {0} sh -c 'cp /data/local/tmp/pa-restore.bin files/{1}'" -f $PaTwinPackage, $to) 30000
    Invoke-PaShell 'rm -f /data/local/tmp/pa-restore.bin' | Out-Null
    if ($r.Text.Trim() -ne '') { throw ("restore: " + $r.Text.Trim()) }
}

function Invoke-PaOpInstall($Step) {
    Assert-PaTwinTarget $script:Pa.Package
    $apk = [string](Get-PaProp $Step 'apk' '')
    if ($apk -eq '' -or -not (Test-Path -LiteralPath $apk)) { throw 'install: apk not found' }
    # the twin and the owner-debug build leave their APK at the same path: only an APK that declares the twin's package is installed here
    Assert-PaInstallablePackage (Get-PaApkPackage $apk)
    $r = Invoke-PaAdb @('install', '-r', $apk) 240000
    if ($r.Text -cnotmatch 'Success') { throw ("install: " + $r.Text.Trim()) }
}

# The system file picker: best effort, flagged fragile in the scenarios. Taps the confirm button of the picker or the file by name.
function Invoke-PaOpPick($Step) {
    Assert-PaTwinTarget $script:Pa.Package
    $mode = [string](Get-PaProp $Step 'save' '')
    $name = [string](Get-PaProp $Step 'name' '')
    if ($name -ne '') {
        $w = Wait-PaNode ([pscustomobject]@{ scope = 'app'; textContains = $name }) 3
        if (-not $w.Ok) {
            $d = Get-PaDump
            $hit = $null
            if ($null -ne $d) { $hit = Find-PaNode $d ([pscustomobject]@{ scope = 'app'; textContains = $name }) }
            if ($null -ne $hit) { $c = Get-PaNodeCenter $hit; Send-PaTap $c[0] $c[1]; Start-Sleep -Milliseconds 500 }
        }
    }
    $btn = [pscustomobject]@{ re = '^(Save|SAVE|Kaydet|Enregistrer|Select|SELECT|Se.|Done|Bitti|OK)$' }
    $d2 = Get-PaDump
    $node = $null
    if ($null -ne $d2) { $node = Find-PaNode $d2 ([pscustomobject]@{ scope = 'app'; re = $btn.re }) }
    if ($null -eq $node) { throw 'pick: the file picker has no confirm button the robot knows; do this step by hand (flag manual-fallback)' }
    $c2 = Get-PaNodeCenter $node
    Send-PaTap $c2[0] $c2[1]
    Start-Sleep -Milliseconds 800
}

function Invoke-PaOpAdb($Step) {
    Assert-PaTwinTarget $script:Pa.Package
    $argv = @(Get-PaProp $Step 'args' @() | ForEach-Object { [string]$_ })
    if ($argv.Count -eq 0) { throw 'adb: args is empty' }
    # an allow list of exact shapes, each bound to the package of the session (Test-PaAdbShape); everything else is refused
    $why = Test-PaAdbShape $argv $script:Pa.Package $script:Pa.Owner
    if ($why -ne '') { throw ('refused: adb: ' + $why) }
    $r = Invoke-PaAdb $argv 60000
    $want = [int](Get-PaProp $Step 'expectExit' 0)
    if ($r.ExitCode -ne $want) { throw ("adb: exit {0}, wanted {1}: {2}" -f $r.ExitCode, $want, $r.Text.Trim()) }
}

# ---- practice mode through the interface (never through a file) -------------------------------------------------
# Turns the practice switch to $On through Settings > Runtime (nothing is typed). Returns $true when it had to be tapped.
function Set-PaPracticeSwitch([bool]$On) {
    $sel = [pscustomobject]@{ tag = 'settings.runtime.practice' }
    $tab = Wait-PaNode ([pscustomobject]@{ any = @([pscustomobject]@{ tag = 'tab.settings' }, [pscustomobject]@{ desc = 'Settings' }) }) 15
    if (-not $tab.Ok) { throw 'practice: the Settings tab is not reachable' }
    $c = Get-PaNodeCenter $tab.Node; Send-PaTap $c[0] $c[1]; Start-Sleep -Milliseconds 500
    $sec = Wait-PaNode ([pscustomobject]@{ any = @([pscustomobject]@{ tag = 'settings.section.runtime' }, [pscustomobject]@{ text = 'Runtime' }) }) 10
    if (-not $sec.Ok) { throw 'practice: the Runtime section is not reachable' }
    $c = Get-PaNodeCenter $sec.Node; Send-PaTap $c[0] $c[1]; Start-Sleep -Milliseconds 500
    $sw = Wait-PaNode $sel 10
    if (-not $sw.Ok) { throw 'practice: the practice switch (settings.runtime.practice) is not on screen' }
    $is = Get-PaNodeFlag $sw.Node 'checked'
    if ($is -eq $On) { return $false }
    $c = Get-PaNodeCenter $sw.Node; Send-PaTap $c[0] $c[1]; Start-Sleep -Milliseconds 800
    # the switch itself must now show the wanted state (a tap that missed is caught here, before anything is typed)
    $after = Wait-PaNode $sel 10
    if (-not $after.Ok) { throw 'practice: the practice switch disappeared after the tap' }
    if ((Get-PaNodeFlag $after.Node 'checked') -ne $On) { throw 'practice: the practice switch did not change after the tap' }
    return $true
}

function Wait-PaRunning([int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PaStateMatches (Get-PaCurrentState) 'Running') { return }
        Start-Sleep -Seconds 2
    }
    throw ("Hermes did not reach Running within {0}s" -f $TimeoutSec)
}

# Switches practice mode on or off through the interface and proves it from app.log (practice=on|off). Throws when it cannot be proven.
function Set-PaPracticeMode([bool]$On) {
    $want = 'off'
    if ($On) { $want = 'on' }
    if ($On -and $script:Pa.Owner -and (Test-PaOwnerAlarmGuard)) { throw 'refused: an alarm of the owner package is due within 30 minutes (or the alarm list is unreadable)' }
    $changed = Set-PaPracticeSwitch $On
    if ($changed) { Start-Sleep -Seconds 6; Wait-PaRunning 240 }
    $seen = Get-PaPracticeFromLog (Get-PaAppLog)
    if ($seen -ne $want) { throw ("practice={0} was not confirmed in app.log (last line says '{1}')" -f $want, $seen) }
    $homeTab = Wait-PaNode ([pscustomobject]@{ any = @([pscustomobject]@{ tag = 'tab.home' }, [pscustomobject]@{ desc = 'Home' }) }) 5
    if ($homeTab.Ok) { $c = Get-PaNodeCenter $homeTab.Node; Send-PaTap $c[0] $c[1] }
    return $changed
}

# Practice mode on for a scenario flagged "practice": through the interface, proven from app.log, remembered so the runner turns it off again.
function Invoke-PaPracticeOn {
    # marked BEFORE the switch is touched: if anything after the tap throws, the runner still turns practice off at the end
    $script:Pa.PracticeTurnedOn = $true
    [void](Set-PaPracticeMode $true)
}

# ---- scenarios ------------------------------------------------------------------------------------------------
function Invoke-PaStep($Step, [int]$Depth = 0) {
    $script:Pa.StepNo = $script:Pa.StepNo + 1
    $step = Expand-PaStep $Step
    $op = [string](Get-PaProp $step 'op' '')
    if ($script:PaOps -cnotcontains $op) { throw ("unknown op '{0}'" -f $op) }
    # the focus guard needs to know the op (the system surfaces an op may meet are listed per op); nested steps restore the outer one
    $outerOp = [string]$script:Pa.CurrentOp
    $script:Pa.CurrentOp = $op
    try {
        switch ($op) {
            'link' { Invoke-PaOpLink $step }
            'tap' { Invoke-PaOpTap $step }
            'type' { Invoke-PaOpType $step }
            'wait' { Invoke-PaOpWait $step }
            'expect' { Invoke-PaOpExpect $step $false }
            'expectNot' { Invoke-PaOpExpect $step $true }
            'shot' { Save-PaShot ([string](Get-PaProp $step 'name' 'shot')) | Out-Null }
            'kill' { Invoke-PaOpKill }
            'forceStop' {
                # a force-stop cancels the app's alarms (AGENTS.md): never on a package that is not the twin; the scenario goes on from where the app is
                if ($script:Pa.Package -eq $PaTwinPackage) { Invoke-PaOpForceStop }
                else { Write-Host ("  note: forceStop skipped on {0} (it would cancel the owner's alarms)" -f $script:Pa.Package) }
            }
            'notification' { Invoke-PaOpNotification $step }
            'noCrash' { Invoke-PaOpNoCrash }
            'key' { Send-PaKey ([string](Get-PaProp $step 'key' '')) }
            'swipe' { Invoke-PaOpSwipe $step }
            'scale' { Invoke-PaOpScale $step }
            'a11y' { Invoke-PaOpA11y }
            'scanDump' { Invoke-PaOpScanDump $step }
            'expectColor' { Invoke-PaOpExpectColor $step }
            'wipe' { Invoke-PaOpWipe $step }
            'pref' { Invoke-PaOpPref $step }
            'seed' { Invoke-PaOpSeed $step }
            'killGuest' { Invoke-PaOpKillGuest }
            'expectFile' { Invoke-PaOpExpectFile $step }
            'restore' { Invoke-PaOpRestore $step }
            'install' { Invoke-PaOpInstall $step }
            'pick' { Invoke-PaOpPick $step }
            'adb' { Invoke-PaOpAdb $step }
            'measure' { Invoke-PaOpMeasure $step }
            'eachRoute' { Invoke-PaOpEachRoute $step $Depth }
        }
    } finally { $script:Pa.CurrentOp = $outerOp }
}

function Invoke-PaOpMeasure($Step) {
    $a = [string](Get-PaProp $Step 'action' '')
    if ($null -eq $script:Pa.Sampler) { return }
    switch ($a) {
        'start' { $script:Pa.Sampler.Active = $true; $script:Pa.Sampler.Explicit = $true; Invoke-PaSamplerReset }
        'stop' { Invoke-PaSamplerPoll; $script:Pa.Sampler.Active = $false }
        default { throw 'measure: action is start or stop' }
    }
}

function Read-PaRoutes {
    $f = Join-Path (Join-Path (Join-Path (Get-PaRepoRoot) 'tools') 'scenarios') 'routes.json'
    return @((Get-PaProp (Read-PaJsonFile $f) 'routes' @()))
}

# For each route of routes.json: set {{var:route}} and {{var:screen}}, run the nested steps. A route marked optional is skipped when
# its placeholder cannot be filled (no chat yet).
function Invoke-PaOpEachRoute($Step, [int]$Depth) {
    if ($Depth -gt 0) { throw 'eachRoute cannot be nested' }
    $inner = @(Get-PaProp $Step 'steps' @())
    $script:Pa.Vars.Remove('route'); $script:Pa.Vars.Remove('screen')
    foreach ($r in (Read-PaRoutes)) {
        $route = [string](Get-PaProp $r 'route' '')
        $screen = [string](Get-PaProp $r 'screen' '')
        if ($route.Contains('{{latestChat}}')) {
            $id = Get-PaLatestChat
            if ($id -eq '') { if ([bool](Get-PaProp $r 'optional' $false)) { continue } else { throw 'eachRoute: there is no chat for chat:{{latestChat}}' } }
            $route = $route.Replace('{{latestChat}}', $id)
        }
        $script:Pa.Vars['route'] = $route
        $script:Pa.Vars['screen'] = $screen
        foreach ($s in $inner) { Invoke-PaStep $s ($Depth + 1) }
    }
    $script:Pa.Vars.Remove('route'); $script:Pa.Vars.Remove('screen')
}

# After a scenario that changed the font or the animation scales: back to what the run started with (the owner's font scale; animations off
# for functional runs). The backup file written by Set-PaSystemScales holds the original values.
function Reset-PaScenarioScales {
    if (-not $script:Pa.ScenarioScaled) { return }
    $script:Pa.ScenarioScaled = $false
    $backup = Get-PaScalesBackupPath
    if (-not (Test-Path -LiteralPath $backup)) { return }
    $orig = Read-PaJsonFile $backup
    $vals = @{ font_scale = [string](Get-PaProp $orig 'font_scale' '1.0') }
    if ($script:Pa.FunctionalScales) {
        $vals['animator_duration_scale'] = '0'; $vals['transition_animation_scale'] = '0'; $vals['window_animation_scale'] = '0'
    } else {
        $vals['animator_duration_scale'] = [string](Get-PaProp $orig 'animator_duration_scale' '1.0')
        $vals['transition_animation_scale'] = [string](Get-PaProp $orig 'transition_animation_scale' '1.0')
        $vals['window_animation_scale'] = [string](Get-PaProp $orig 'window_animation_scale' '1.0')
    }
    Set-PaSystemScales $vals
}

# Runs one scenario; returns the result object (Name, Status PASS|FAIL|SKIP, Seconds, Detail, Shot).
function Invoke-PaScenario($Scn, [string]$ScenarioDir) {
    $name = [string](Get-PaProp $Scn 'name' '?')
    $script:Pa.ScenarioName = $name
    $script:Pa.ScenarioDir = $ScenarioDir
    New-PaDir $ScenarioDir
    $script:Pa.StepNo = 0; $script:Pa.ShotNo = 0
    $script:Pa.LogcatText = ''
    $script:Pa.Leaks = New-Object System.Collections.ArrayList
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $result = [pscustomobject]@{ Name = $name; Status = 'PASS'; Seconds = 0.0; Detail = ''; Shot = '' }
    $timeout = [int](Get-PaProp $Scn 'timeoutSec' 240)
    $addedVars = @()
    $script:Pa.CapturedVars = New-Object System.Collections.ArrayList
    $defaults = Get-PaProp $Scn 'vars' $null
    foreach ($vn in (Get-PaPropNames $defaults)) {
        if (-not $script:Pa.Vars.ContainsKey($vn)) { $script:Pa.Vars[$vn] = [string](Get-PaProp $defaults $vn ''); $addedVars += $vn }
    }
    try {
        Invoke-PaAdb @('logcat', '-c') 20000 | Out-Null
        $script:Pa.StartStamp = Get-PaDeviceStamp
        $passes = @($false)
        if ([bool](Get-PaProp $Scn 'cutCheck' $false) -and -not $script:Pa.NoCut) { $passes = @($false, $true) }
        foreach ($cut in $passes) {
            $script:Pa.CutMode = $cut
            if ($cut) {
                if (-not (Test-PaSystemSettingsAllowed)) {
                    $result.Detail = 'cut check at font 2.0 skipped: needs -AllowSystemSettings on the owner package'
                    break
                }
                Set-PaSystemScales @{ font_scale = '2.0' }
                $script:Pa.ScenarioScaled = $true
                $script:Pa.SeenScreens = @{}
            }
            foreach ($s in @(Get-PaProp $Scn 'steps' @())) {
                if ($sw.Elapsed.TotalSeconds -gt $timeout) { throw ("scenario timeout ({0}s)" -f $timeout) }
                Invoke-PaStep $s 0
            }
        }
        if ($script:Pa.Leaks.Count -gt 0) {
            $result.Status = 'FAIL'
            $result.Detail = ('scanDump: {0} plain text(s) left in the stretched language, first: {1}' -f $script:Pa.Leaks.Count, $script:Pa.Leaks[0])
            foreach ($l in $script:Pa.Leaks) { Write-Host $l }
        }
    } catch {
        $result.Status = 'FAIL'
        $msg = $_.Exception.Message
        $result.Detail = ('step {0} {1}' -f $script:Pa.StepNo, $msg)
        try { $result.Shot = Save-PaShot 'fail'; Save-PaFailureDump | Out-Null } catch { }
    } finally {
        $script:Pa.CutMode = $false
        foreach ($vn in $addedVars) { $script:Pa.Vars.Remove($vn) }
        # cleanupChain: the steps guard each other (a first step that proves where we are), so the first failure ends the cleanup
        $chain = [bool](Get-PaProp $Scn 'cleanupChain' $false)
        foreach ($cs in @(Get-PaProp $Scn 'cleanup' @())) {
            $cleanFailed = $false
            try { Invoke-PaStep $cs 0 } catch { $cleanFailed = $true; Write-Host ('NOTE cleanup step failed: ' + $_.Exception.Message) }
            if ($cleanFailed -and $chain) { Write-Host 'NOTE the cleanup stopped at its first failed step'; break }
        }
        foreach ($cv in @($script:Pa.CapturedVars)) { $script:Pa.Vars.Remove($cv) }
        try { Reset-PaScenarioScales } catch { Write-Host ('NOTE could not reset the scales after the scenario: ' + $_.Exception.Message) }
        try { Update-PaLogcat } catch { }
        try { Write-PaTextFile (Join-Path $ScenarioDir 'logcat.txt') $script:Pa.LogcatText } catch { }
    }
    $sw.Stop()
    $result.Seconds = [Math]::Round($sw.Elapsed.TotalSeconds, 1)
    return $result
}

# ---- scenario files ---------------------------------------------------------------------------------------------
function Get-PaScenarioRoot { return (Join-Path (Join-Path (Get-PaRepoRoot) 'tools') 'scenarios') }

# Every file of tools/scenarios (not perf/) that has a name and steps.
function Read-PaScenarios([string]$Dir = '') {
    if ($Dir -eq '') { $Dir = Get-PaScenarioRoot }
    $list = @()
    foreach ($f in (Get-ChildItem -LiteralPath $Dir -Filter '*.json' -File | Sort-Object Name)) {
        $j = Read-PaJsonFile $f.FullName
        if ((Test-PaHas $j 'name') -and (Test-PaHas $j 'steps')) { $list += [pscustomobject]@{ File = $f.Name; Scenario = $j } }
    }
    return $list
}

# Problems of one scenario file (empty array when fine). Used by the self test and by the runner before it starts.
function Test-PaScenarioShape($Scn, [string]$FileName) {
    $bad = @()
    $name = [string](Get-PaProp $Scn 'name' '')
    if ($name -eq '') { $bad += 'no name' }
    elseif (($name + '.json') -ne $FileName) { $bad += ("name '{0}' differs from the file name" -f $name) }
    $steps = @(Get-PaProp $Scn 'steps' @())
    $kind = [string](Get-PaProp (Get-PaProp $Scn 'measure' $null) 'kind' '')
    if ($steps.Count -eq 0 -and $kind -ne 'coldstart') { $bad += 'no steps' }
    $all = New-Object System.Collections.ArrayList
    foreach ($s in $steps) {
        [void]$all.Add($s)
        foreach ($i in @(Get-PaProp $s 'steps' @())) { [void]$all.Add($i) }
    }
    foreach ($s in $all) {
        $op = [string](Get-PaProp $s 'op' '')
        if ($script:PaOps -cnotcontains $op) { $bad += ("unknown op '{0}'" -f $op); continue }
        if (@('tap', 'wait', 'expect', 'expectNot') -contains $op) {
            $hasOther = (Test-PaHas $s 'ms') -or (Test-PaHas $s 'state') -or (Test-PaHas $s 'log') -or (Test-PaHas $s 'navIs') -or (Test-PaHas $s 'chatHas')
            if (-not (Test-PaSelectorHasField $s) -and -not $hasOther) { $bad += ("op {0} has no selector" -f $op) }
        }
    }
    return $bad
}

# ---- run state and report ----------------------------------------------------------------------------------------
function Initialize-PaRun([hashtable]$Options) {
    foreach ($k in $Options.Keys) { $script:Pa[$k] = $Options[$k] }
    $script:Pa.A11y = New-Object System.Collections.ArrayList
    $script:Pa.Cut = New-Object System.Collections.ArrayList
    $script:Pa.SeenScreens = @{}
    $root = Get-PaScenarioRoot
    $script:Pa.A11yAllow = @(Read-PaAllowFile (Join-Path $root 'a11y-allow.json'))
    $script:Pa.CutAllow = @(Read-PaAllowFile (Join-Path $root 'cut-allow.json'))
}

function Write-PaReport([string]$OutDir, $Results, [string]$VersionName, [string]$Package, [bool]$Owner) {
    $a11y = @($script:Pa.A11y)
    $cut = @($script:Pa.Cut)
    $pass = @($Results | Where-Object { $_.Status -eq 'PASS' }).Count
    $fail = @($Results | Where-Object { $_.Status -eq 'FAIL' }).Count
    $skip = @($Results | Where-Object { $_.Status -eq 'SKIP' }).Count
    $obj = [ordered]@{
        schema = 1; package = $Package; versionName = $VersionName; owner = $Owner
        summary = [ordered]@{ pass = $pass; fail = $fail; skip = $skip; a11y = $a11y.Count; cut = $cut.Count }
        scenarios = @($Results | ForEach-Object { [ordered]@{ name = $_.Name; status = $_.Status; seconds = $_.Seconds; detail = $_.Detail; shot = $_.Shot } })
        a11y = $a11y; cut = $cut
    }
    Write-PaJsonFile (Join-Path $OutDir 'report.json') $obj
    $md = New-Object System.Text.StringBuilder
    [void]$md.AppendLine('# Phone test report')
    [void]$md.AppendLine('')
    [void]$md.AppendLine(('Package `{0}`, version `{1}`. pass={2} fail={3} skip={4} a11y={5} cut={6}' -f $Package, $VersionName, $pass, $fail, $skip, $a11y.Count, $cut.Count))
    [void]$md.AppendLine('')
    [void]$md.AppendLine('| Scenario | Result | Seconds | Detail |')
    [void]$md.AppendLine('|---|---|---|---|')
    foreach ($r in $Results) { [void]$md.AppendLine(('| {0} | {1} | {2} | {3} |' -f $r.Name, $r.Status, (Format-PaMs $r.Seconds), ($r.Detail -replace '\|', '/'))) }
    if ($a11y.Count -gt 0) { [void]$md.AppendLine(''); [void]$md.AppendLine('## Spoken labels'); foreach ($l in $a11y) { [void]$md.AppendLine('- ' + $l) } }
    if ($cut.Count -gt 0) { [void]$md.AppendLine(''); [void]$md.AppendLine('## Cut text'); foreach ($l in $cut) { [void]$md.AppendLine('- ' + $l) } }
    Write-PaTextFile (Join-Path $OutDir 'report.md') $md.ToString()
}

# ============================================================================================================
# 6. frame statistics (gfxinfo framestats)
# ============================================================================================================

# The profile blocks of `dumpsys gfxinfo <pkg> framestats` into a list of frames: IntendedVsync (ns) and DurationMs. Rows whose Flags has
# bit 0 set are skipped (the first frame of an animation and similar). Frames are de-duplicated by IntendedVsync (two polls overlap).
function ConvertFrom-PaFrameStats([string]$Text) {
    $frames = New-Object 'System.Collections.Generic.Dictionary[string,double]'
    $inBlock = $false
    $idxFlags = -1; $idxIntended = -1; $idxDone = -1
    foreach ($raw in ($Text -csplit "`n")) {
        $l = $raw.Trim()
        if ($l -eq '---PROFILEDATA---') { $inBlock = -not $inBlock; $idxFlags = -1; continue }
        if (-not $inBlock) { continue }
        if ($l.StartsWith('Flags,')) {
            $cols = $l.Split(',')
            for ($i = 0; $i -lt $cols.Length; $i++) {
                switch ($cols[$i]) { 'Flags' { $idxFlags = $i } 'IntendedVsync' { $idxIntended = $i } 'FrameCompleted' { $idxDone = $i } }
            }
            continue
        }
        if ($idxFlags -lt 0 -or $l -eq '') { continue }
        $f = $l.Split(',')
        if ($f.Length -le $idxDone -or $f.Length -le $idxIntended) { continue }
        $flags = 0L
        if (-not [long]::TryParse($f[$idxFlags], [ref]$flags)) { continue }
        if (($flags -band 1) -ne 0) { continue }
        $a = 0L; $b = 0L
        if (-not [long]::TryParse($f[$idxIntended], [ref]$a)) { continue }
        if (-not [long]::TryParse($f[$idxDone], [ref]$b)) { continue }
        if ($b -le $a) { continue }
        $key = [string]$a
        if (-not $frames.ContainsKey($key)) { $frames[$key] = (($b - $a) / 1000000.0) }
    }
    return $frames
}

function Get-PaPercentile([double[]]$Sorted, [double]$P) {
    if ($Sorted.Length -eq 0) { return 0.0 }
    $rank = [int][Math]::Ceiling($P / 100.0 * $Sorted.Length)
    if ($rank -lt 1) { $rank = 1 }
    return $Sorted[$rank - 1]
}

# Statistics of a list of durations (ms).
function Get-PaFrameSummary([double[]]$Durations) {
    $sorted = @($Durations | Sort-Object)
    $n = $sorted.Count
    $within = 0; $o167 = 0; $o33 = 0
    foreach ($d in $sorted) { if ($d -le 8.3) { $within++ }; if ($d -gt 16.7) { $o167++ }; if ($d -gt 33.0) { $o33++ } }
    $pct = 0.0
    if ($n -gt 0) { $pct = [Math]::Round(100.0 * $within / $n, 1) }
    $arr = [double[]]$sorted
    return [pscustomobject]@{
        Frames = $n; Within8_3Pct = $pct
        P50 = (Get-PaPercentile $arr 50); P90 = (Get-PaPercentile $arr 90); P95 = (Get-PaPercentile $arr 95); P99 = (Get-PaPercentile $arr 99)
        Worst = $(if ($n -gt 0) { $arr[$n - 1] } else { 0.0 })
        Over16_7 = $o167; Over33 = $o33
    }
}

# The frame sampler used by phone-perf: Poll adds the frames of one `dumpsys gfxinfo framestats` to a dictionary keyed by IntendedVsync.
function New-PaSampler {
    return [pscustomobject]@{ Active = $true; Explicit = $false; Frames = (New-Object 'System.Collections.Generic.Dictionary[string,double]'); Polls = 0 }
}

function Invoke-PaSamplerReset {
    $s = $script:Pa.Sampler
    if ($null -eq $s) { return }
    $s.Frames.Clear()
    $s.Polls = 0
    Invoke-PaShell ("dumpsys gfxinfo {0} reset" -f $script:Pa.Package) | Out-Null
}

function Invoke-PaSamplerPoll {
    $s = $script:Pa.Sampler
    if ($null -eq $s -or -not $s.Active) { return }
    $r = Invoke-PaShell ("dumpsys gfxinfo {0} framestats" -f $script:Pa.Package) 30000
    $f = ConvertFrom-PaFrameStats $r.Out
    foreach ($k in $f.Keys) { if (-not $s.Frames.ContainsKey($k)) { $s.Frames[$k] = $f[$k] } }
    $s.Polls = $s.Polls + 1
}

# ============================================================================================================
# 7. self test (no phone)
# ============================================================================================================

function Assert-PaSelf([bool]$Cond, [string]$Message) {
    if (-not $Cond) { throw ("SELFTEST FAIL: " + $Message) }
}

# The lines of a script that could get an input command to the phone without the focus guard: a device input command spelled outside
# Invoke-PaInput, or a direct call of adb through Invoke-PaNative outside Invoke-PaAdb and Get-PaDevices. Comment lines are skipped.
# Returns "<line>: <what>" strings.
function Get-PaInputLeaks([string]$Text) {
    $hits = New-Object System.Collections.ArrayList
    $fn = ''
    $inBlock = $false
    $no = 0
    foreach ($raw in ($Text -csplit "`n")) {
        $no++
        $l = $raw.TrimEnd("`r")
        $t = $l.Trim()
        if ($inBlock) { if ($t.Contains('#>')) { $inBlock = $false }; continue }
        if ($t.StartsWith('<#')) { if (-not $t.Contains('#>')) { $inBlock = $true }; continue }
        if ($t.StartsWith('#')) { continue }
        $oneLine = $false
        $h = [regex]::Match($l, '^function\s+([A-Za-z0-9_-]+)')
        if ($h.Success) {
            $fn = $h.Groups[1].Value
            $opens = @($l.ToCharArray() | Where-Object { $_ -eq '{' }).Count
            $closes = @($l.ToCharArray() | Where-Object { $_ -eq '}' }).Count
            if ($opens -gt 0 -and $opens -eq $closes) { $oneLine = $true }
        }
        if ($fn -cne 'Invoke-PaInput') {
            if ($l -cmatch '(?i)\bshell\s+input\b' -or $l -cmatch '(?i)\binput\s+(tap|swipe|text|keyevent|press|roll|draganddrop)\b') {
                [void]$hits.Add(("{0}: spells an input command outside Invoke-PaInput (in '{1}')" -f $no, $fn))
            }
        }
        if (@('Invoke-PaAdb', 'Get-PaDevices') -cnotcontains $fn -and $l -cmatch 'Invoke-PaNative\s+\$script:Pa\.Adb') {
            [void]$hits.Add(("{0}: calls adb directly through Invoke-PaNative (in '{1}')" -f $no, $fn))
        }
        if ($oneLine -or $l -ceq '}') { $fn = '' }
    }
    return @($hits)
}

# Self test of the focus guard, the input choke point, the adb allow list and the install check. No phone: nothing here reaches adb.
function Invoke-PaGuardSelfTest {
    $twin = $PaTwinPackage
    $ownerPkg = $PaOwnerPackage
    $script:Pa.Package = $twin
    $script:Pa.Halted = $false; $script:Pa.FocusRefused = $false; $script:Pa.HaltReason = ''; $script:Pa.CurrentOp = ''; $script:Pa.InputOk = $false
    $main = $PaMainActivity
    $nl = "`n"

    # ---- the focus text of the phone into a verdict
    $appWin = 'mCurrentFocus=Window{4b3c8c1 u0 ' + $twin + '/' + $main + '}'
    $appRec = 'mFocusedApp=ActivityRecord{1a2b3c u0 ' + $twin + '/' + $main + ' t12}'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ($appWin + $nl + $appRec + $nl)) $twin @() ''
    Assert-PaSelf ($v.Ok -and $v.Kind -eq 'ok') 'focus: the app window and the app activity are accepted'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ('  ' + $appWin + $nl + '  ' + $appWin + $nl + '  ' + $appRec + $nl)) $twin @() ''
    Assert-PaSelf $v.Ok 'focus: the same window listed twice (several displays) is accepted'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ('mCurrentFocus=Window{5c u0 ' + $twin + '}' + $nl + $appRec)) $twin @() ''
    Assert-PaSelf ($v.Ok -and $v.Kind -eq 'ok') 'focus: a dialog of the app (window named by the package alone) is accepted without a lookup'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ('mCurrentFocus=Window{5c u0 ' + $ownerPkg + '}')) $twin @() ''
    Assert-PaSelf ((-not $v.Ok) -and $v.Kind -eq 'foreign') 'focus: a dialog of the owner package is not a dialog of the twin'
    $launcher = 'mCurrentFocus=Window{9d8e7f u0 com.sec.android.app.launcher/com.sec.android.app.launcher.activities.LauncherActivity}'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ($launcher + $nl + 'mFocusedApp=ActivityRecord{55aa u0 com.sec.android.app.launcher/.activities.LauncherActivity t2}')) $twin @() ''
    Assert-PaSelf ((-not $v.Ok) -and $v.Kind -eq 'foreign' -and $v.Why.Contains('com.sec.android.app.launcher')) 'focus: a launcher window is refused and named'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ('mCurrentFocus=Window{33cc u0 ' + $ownerPkg + '/' + $main + '}')) $twin @() ''
    Assert-PaSelf ((-not $v.Ok) -and $v.Kind -eq 'foreign') 'focus: the owner package is not the twin (a prefix is not a match)'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ('mCurrentFocus=Window{33cc u0 ' + $twin + '/' + $main + '}')) $ownerPkg @() ''
    Assert-PaSelf ((-not $v.Ok) -and $v.Kind -eq 'foreign') 'focus: the twin is not the owner package'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ('mCurrentFocus=null' + $nl + 'mFocusedApp=null' + $nl)) $twin @() ''
    Assert-PaSelf ((-not $v.Ok) -and $v.Kind -eq 'none') 'focus: no focused window is its own kind (the caller reads again)'
    $v = Test-PaFocusFacts (Get-PaFocusFacts '') $twin @() ''
    Assert-PaSelf ((-not $v.Ok) -and $v.Kind -eq 'none') 'focus: unreadable text is refused'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ($appWin + $nl + 'mFocusedApp=ActivityRecord{55aa u0 com.sec.android.app.launcher/.activities.LauncherActivity t2}')) $twin @() ''
    Assert-PaSelf ((-not $v.Ok) -and $v.Why.Contains('focused activity')) 'focus: the second source (mFocusedApp) must agree'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ($appWin + $nl + $launcher)) $twin @() ''
    Assert-PaSelf (-not $v.Ok) 'focus: two different focused windows are refused'
    $shade = 'mCurrentFocus=Window{71 u0 NotificationShade}'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ($shade + $nl + $appRec)) $twin @() ''
    Assert-PaSelf ((-not $v.Ok) -and $v.Why.Contains('com.android.systemui')) 'focus: the notification shade is refused for an ordinary op'
    $v = Test-PaFocusFacts (Get-PaFocusFacts ($shade + $nl + $appRec)) $twin @(Get-PaFocusExtra 'notification') ''
    Assert-PaSelf $v.Ok 'focus: the notification shade is accepted for the notification op'
    Assert-PaSelf (@(Get-PaFocusExtra 'tap').Count -eq 0 -and @(Get-PaFocusExtra 'notification') -ccontains 'com.android.systemui') 'focus: the allow table is per op'
    # a window whose name has no package: the package comes from the window list
    $popup = 'mCurrentFocus=Window{9f9f u0 Popup Window}'
    $facts = Get-PaFocusFacts $popup
    Assert-PaSelf ((Test-PaFocusFacts $facts $twin @() '').Kind -eq 'lookup') 'focus: a window without a package in its name asks for the window list'
    $list = '  Window #3 Window{9f9f u0 Popup Window}:' + $nl + '    mDisplayId=0 rootTaskId=7' + $nl + '    mOwnerUid=10123 showForAllUsers=false package=' + $twin + ' appop=NONE' + $nl +
        '  Window #4 Window{aaaa u0 Other}:' + $nl + '    mOwnerUid=10999 showForAllUsers=false package=com.example.other appop=NONE' + $nl
    Assert-PaSelf ((Get-PaWindowPackage $list '9f9f') -eq $twin) 'focus: the window list names the owner of a window'
    Assert-PaSelf ((Get-PaWindowPackage $list 'aaaa') -eq 'com.example.other') 'focus: the window list reads the last block too'
    Assert-PaSelf ((Get-PaWindowPackage $list 'bbbb') -eq '') 'focus: an unknown window has no package'
    Assert-PaSelf ((Test-PaFocusFacts $facts $twin @() $list).Ok) 'focus: a popup of the app is accepted through the window list'
    $other = $list.Replace('package=' + $twin, 'package=com.example.other')
    Assert-PaSelf (-not (Test-PaFocusFacts $facts $twin @() $other).Ok) 'focus: a popup of another package is refused'
    $crash = 'mCurrentFocus=Window{c1 u0 Application Error: ' + $twin + '}'
    $v = Test-PaFocusFacts (Get-PaFocusFacts $crash) $twin @() ('  Window #0 Window{c1 u0 Application Error: ' + $twin + '}:' + $nl + '    mOwnerUid=1000 showForAllUsers=true package=android appop=NONE' + $nl)
    Assert-PaSelf ((-not $v.Ok) -and $v.Why.Contains('android')) 'focus: the crash dialog (package android) is refused although its title names the app'
    $v = Test-PaFocusFacts (Get-PaFocusFacts $crash) $twin @() 'nothing useful here'
    Assert-PaSelf ((-not $v.Ok) -and $v.Kind -eq 'foreign') 'focus: a window whose package cannot be read is refused'

    # ---- every input goes through Invoke-PaInput and stops when the run is stopped
    $in = 'in' + 'put'
    Assert-PaSelf (Test-PaAdbInputCommand @('shell', ($in + ' tap 1 1'))) 'input guard: the one-argument form is recognised'
    Assert-PaSelf (Test-PaAdbInputCommand @('-s', 'x', 'shell', $in, 'keyevent', '4')) 'input guard: the split form is recognised'
    Assert-PaSelf (Test-PaAdbInputCommand @('shell', ('sleep 1; ' + $in + ' swipe 1 2 3 4'))) 'input guard: a command after a semicolon is recognised'
    Assert-PaSelf (Test-PaAdbInputCommand @('shell', ('sh -c ''' + $in + ' text a'''))) 'input guard: a command inside sh -c is recognised'
    Assert-PaSelf (-not (Test-PaAdbInputCommand @('shell', "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"))) 'input guard: the focus read is not an input command'
    Assert-PaSelf (-not (Test-PaAdbInputCommand @('shell', 'uiautomator dump /data/local/tmp/pa-dump.xml'))) 'input guard: a dump is not an input command'
    $refused = $false
    try { Invoke-PaAdb @('shell', ($in + ' tap 1 1')) | Out-Null } catch { $refused = $_.Exception.Message.StartsWith('refused:') }
    Assert-PaSelf $refused 'input guard: Invoke-PaAdb refuses an input command that did not come from Invoke-PaInput'
    $refused = $false
    try { Invoke-PaShell ($in + ' keyevent 4') | Out-Null } catch { $refused = $_.Exception.Message.StartsWith('refused:') }
    Assert-PaSelf $refused 'input guard: Invoke-PaShell cannot be used to type either'
    $script:Pa.Halted = $true; $script:Pa.HaltReason = 'selftest'
    foreach ($send in @({ Send-PaTap 1 1 }, { Send-PaKey 'BACK' }, { Send-PaText 'abc' }, { Invoke-PaInput 'swipe' '1 2 3 4 5' })) {
        $refused = $false
        try { & $send } catch { $refused = $_.Exception.Message.StartsWith('refused: focus: input is stopped') }
        Assert-PaSelf $refused 'input guard: a stopped run sends nothing (tap, key, text, swipe)'
    }
    $script:Pa.Halted = $false; $script:Pa.HaltReason = ''
    $refused = $false
    try { Invoke-PaInput 'roll' '1 1' } catch { $refused = $true }
    Assert-PaSelf $refused 'input guard: only tap, swipe, text and keyevent exist'
    $scriptDir = $script:PaLibDir
    $scanned = 0
    foreach ($f in @(Get-ChildItem -LiteralPath $scriptDir -Filter '*.ps1' -File)) {
        $leaks = @(Get-PaInputLeaks (Read-PaTextFile $f.FullName))
        Assert-PaSelf ($leaks.Count -eq 0) ('input guard: ' + $f.Name + ' line ' + $leaks[0])
        $scanned++
    }
    Assert-PaSelf ($scanned -ge 5) 'input guard: the scan saw the tool scripts'
    $planted = ('function Bad {' + $nl + '    Invoke-PaShell ("' + $in + ' tap 1 1")' + $nl + '}' + $nl +
        'function Invoke-PaInput {' + $nl + '    Invoke-PaShell ("' + $in + ' tap 1 1")' + $nl + '}' + $nl +
        'function Worse { Invoke-PaNative $script:Pa.' + 'Adb @(''shell'') }' + $nl + '# ' + $in + ' tap 1 1' + $nl)
    $found = @(Get-PaInputLeaks $planted)
    Assert-PaSelf ($found.Count -eq 2 -and $found[0].StartsWith('2:') -and $found[1].StartsWith('7:')) ('input guard: the scan finds a planted leak and a planted direct adb call, and nothing else (' + ($found -join ' | ') + ')')

    # ---- the free adb op: an allow list bound to the package of the session
    $comp = Get-PaComponent $twin $main
    $ok = @(
        @('devices'), @('logcat', '-d'), @('logcat', '-d', '-v', 'threadtime'), @('logcat', '-d', '-s', 'AgiriCut:W'),
        @('shell', 'dumpsys', 'package', $twin), @('shell', ('dumpsys gfxinfo ' + $twin + ' framestats')), @('shell', 'pidof', $twin),
        @('shell', 'am', 'force-stop', $twin), @('shell', 'am', 'kill', $twin), @('shell', 'am', 'start', '-n', $comp),
        @('shell', 'cmd', 'statusbar', 'collapse'), @('shell', 'getprop', 'ro.build.version.release'), @('shell', 'wm', 'size')
    )
    foreach ($a in $ok) { Assert-PaSelf ((Test-PaAdbShape $a $twin $false) -eq '') ('adb allow list: ' + ($a -join ' ') + ' is allowed') }
    $vi = 'in' + 'stall'
    $vu = 'un' + $vi
    $clearWord = 'cl' + 'ear'
    $set = 'sett' + 'ings'
    $bad = @(
        @($vi, '-r', 'app.apk'), @(($vi + '-multiple'), '-r', '-d', 'app.apk'), @($vu, $ownerPkg), @($vu, $twin),
        @('shell', ('pm${IFS}' + $clearWord + ' ' + $ownerPkg)), @('shell', 'pm', $clearWord, $twin), @('shell', 'pm', 'disable-user', $ownerPkg),
        @('shell', 'cmd', 'package', 'list', 'packages'), @('shell', 'cmd', 'appops', 'set', $ownerPkg, 'RUN_IN_BACKGROUND', 'deny'),
        @('shell', 'am', 'force-stop', $ownerPkg), @('shell', 'am', 'kill', $ownerPkg), @('shell', 'am', 'force-stop', 'com.example.other'),
        @('shell', 'am', 'kill', 'com.example.other'), @('shell', 'am', 'start', '-n', ($ownerPkg + '/.MainActivity')),
        @('shell', 'run-as', $ownerPkg, 'cat', 'files/x'), @('shell', 'run-as', $twin, 'cat', 'files/x'), @('shell', 'rm', '-rf', '/sdcard/x'),
        @('shell', $set, 'put', 'global', 'x', '1'), @('shell', ($in + ' tap 1 1')), @('shell', 'dumpsys', 'package', $ownerPkg),
        @('shell', ('dumpsys package ' + $twin + '; reboot')), @('shell', ('dumpsys package ' + $twin + ' | tee /sdcard/x')), @('shell', ('pidof ' + $twin + ' > /sdcard/x')),
        @('shell', ('dumpsys package ' + $twin + "`nreboot")), @('push', 'a', '/sdcard/a'), @('pull', '/sdcard/a', 'b'), @('reboot'), @('-s', 'x', 'shell', 'getprop', 'a'),
        @('shell', 'dumpsys', 'package', ($twin + '.other')), @('shell', 'dumpsys', 'package', ('x' + $twin)), @('')
    )
    foreach ($a in $bad) { Assert-PaSelf ((Test-PaAdbShape $a $twin $false) -ne '') ('adb allow list: [' + ($a -join ' ') + '] must be refused') }
    Assert-PaSelf ((Test-PaAdbShape @('shell', 'dumpsys', 'meminfo', $ownerPkg) $ownerPkg $true) -eq '') 'adb allow list: a read-only shape for the owner session'
    Assert-PaSelf ((Test-PaAdbShape @('shell', 'am', 'force-stop', $ownerPkg) $ownerPkg $true) -ne '') 'adb allow list: an owner session gets no shape that changes anything'
    Assert-PaSelf ((Test-PaAdbShape @('shell', 'am', 'start', '-n', (Get-PaComponent $ownerPkg $main)) $ownerPkg $true) -ne '') 'adb allow list: an owner session cannot start anything'
    Assert-PaSelf ((Test-PaAdbShape @('shell', 'dumpsys', 'meminfo', $twin) $ownerPkg $true) -ne '') 'adb allow list: the shapes are bound to the package of the session'

    # ---- the install op only takes an APK of the twin
    Assert-PaInstallablePackage $twin
    foreach ($p in @($ownerPkg, 'com.example.other', '', ($twin + '.x'))) {
        $refused = $false
        try { Assert-PaInstallablePackage $p } catch { $refused = $_.Exception.Message.StartsWith('refused: install:') }
        Assert-PaSelf $refused ("install check: the package '" + $p + "' is refused")
    }
}

function Invoke-PaLibSelfTest {
    $root = Get-PaScenarioRoot
    $fixture = Join-Path (Join-Path $root 'fixtures') 'dump-sample.xml'
    $d = ConvertFrom-PaDumpXml (Read-PaTextFile $fixture)
    $script:Pa.Package = $PaTwinPackage
    Assert-PaSelf ($d.Nodes.Count -gt 5) 'the dump fixture parses'
    Assert-PaSelf ($d.Name -eq 'screen.chats') ('screen name is screen.chats, got ' + $d.Name)

    # selectors
    Assert-PaSelf ($null -ne (Find-PaNode $d ([pscustomobject]@{ tag = 'tab.home' }))) 'selector tag'
    Assert-PaSelf ($null -ne (Find-PaNode $d ([pscustomobject]@{ text = 'Rebel' }))) 'selector text'
    Assert-PaSelf ($null -ne (Find-PaNode $d ([pscustomobject]@{ desc = 'Home' }))) 'selector desc'
    Assert-PaSelf ($null -ne (Find-PaNode $d ([pscustomobject]@{ textContains = 'ebe' }))) 'selector textContains'
    Assert-PaSelf ($null -ne (Find-PaNode $d ([pscustomobject]@{ descContains = 'ome' }))) 'selector descContains'
    Assert-PaSelf ($null -ne (Find-PaNode $d ([pscustomobject]@{ re = '^Reb.l$' }))) 'selector re'
    Assert-PaSelf ($null -eq (Find-PaNode $d ([pscustomobject]@{ tag = 'nope' }))) 'selector miss'
    Assert-PaSelf ((Find-PaNode $d ([pscustomobject]@{ any = @([pscustomobject]@{ tag = 'nope' }, [pscustomobject]@{ tag = 'tab.chats' }) })).Tag -eq 'tab.chats') 'selector any: first that matches wins'
    Assert-PaSelf (@(Find-PaNodes $d ([pscustomobject]@{ tag = 'chats.row.0' })).Count -eq 1) 'one row'
    Assert-PaSelf ($null -eq (Find-PaNode $d ([pscustomobject]@{ tag = 'tab.home'; idx = 3 }))) 'selector idx out of range'
    Assert-PaSelf ((Find-PaNode $d ([pscustomobject]@{ tagRe = '^chats\.row\.\d+$'; idx = -1 })).Tag -eq 'chats.row.9') 'tagRe with idx -1 is the last match'
    Assert-PaSelf (@(Find-PaNodes $d ([pscustomobject]@{ tagRe = '^chats\.row\.\d+$' })).Count -eq 10) 'tagRe matches every row'
    Assert-PaSelf ($null -eq (Find-PaNode $d ([pscustomobject]@{ tagRe = '^nothing\.'; idx = -1 }))) 'tagRe without a match, idx -1'
    Assert-PaSelf ($null -ne (Find-PaNode $d ([pscustomobject]@{ scope = 'systemui'; textContains = 'Clock' }))) 'selector scope systemui'
    $sw = Find-PaNode $d ([pscustomobject]@{ tag = 'settings.runtime.practice' })
    Assert-PaSelf ($null -ne $sw) 'switch row found'
    Assert-PaSelf ((Get-PaNodeFlag $sw 'checked') -eq $true) 'checked is read from the first checkable descendant'
    $c = Get-PaNodeCenter (Find-PaNode $d ([pscustomobject]@{ tag = 'tab.home' }))
    Assert-PaSelf ($c[0] -eq 90 -and $c[1] -eq 2250) ('center of bounds, got ' + ($c -join ','))

    # automatic checks: exactly one unlabelled node, exactly one cut text
    $lab = @(Get-PaLabelFindings $d $PaTwinPackage 1080 2340)
    Assert-PaSelf ($lab.Count -eq 1) ('exactly one planted unlabelled node, got ' + $lab.Count)
    Assert-PaSelf ($lab[0].Id -clike 'android.widget.ImageButton@*') ('its id is class@bounds, got ' + $lab[0].Id)
    $probe = @(ConvertFrom-PaCutProbe ("--------- beginning of main`nW/AgiriCut( 4242): Settings and long names`nW/AgiriCut( 4242): Settings and long names`nW/AgiriCut( 4242): Another cut one`nI/Other( 1): AgiriCut mentioned in prose is not a probe line`n"))
    Assert-PaSelf ($probe.Count -eq 2 -and $probe[0] -eq 'Settings and long names' -and $probe[1] -eq 'Another cut one') 'AgiriCut lines are collected once each'
    $cut = @(Get-PaCutFindings $d $PaTwinPackage 1080 2340)
    Assert-PaSelf ($cut.Count -eq 1) ('exactly one planted cut text, got ' + $cut.Count)
    Assert-PaSelf ($cut[0].Text -clike 'A long*') 'the cut text is the planted one'
    Assert-PaSelf (Test-PaAllowed @([pscustomobject]@{ screen = 'screen.chats'; id = $lab[0].Id; reason = 'x' }) 'screen.chats' $d.Sig $lab[0].Id) 'allow list by screen name'
    Assert-PaSelf (-not (Test-PaAllowed @([pscustomobject]@{ screen = 'screen.bots'; id = $lab[0].Id }) 'screen.chats' $d.Sig $lab[0].Id)) 'allow list other screen'

    # placeholders
    $script:Pa.Vars['marker'] = 'X\d'
    $e = Expand-PaStep ([pscustomobject]@{ op = 'tap'; text = '{{pkg}}'; re = '{{var:marker}}'; desc = '{{var:unknown}}' })
    Assert-PaSelf ($e.text -eq $PaTwinPackage) 'placeholder pkg'
    Assert-PaSelf ($e.re -eq 'X\d') 'placeholder var with a backslash'
    Assert-PaSelf ($e.desc -eq '{{var:unknown}}') 'unknown placeholder stays'
    $script:Pa.Vars.Remove('marker')

    # states, logs, alarms, owner typing
    $log = "2027-03-21 10:00:00.000 I/App: log opened (pid 1)`n2027-03-21 10:00:01.000 I/State: Starting(x)`n2027-03-21 10:00:09.000 I/State: Running`n2027-03-21 10:00:10.000 I/App: started Agiri build abc (0.2.1) variant=twin signing=committed package=p practice=off`n"
    Assert-PaSelf ((Get-PaStateFromLog $log) -eq 'Running') 'state from log'
    Assert-PaSelf ((Get-PaVariantFromLog $log) -eq 'twin') 'variant from log'
    $tr = [string][char]0x00C7 + 'al' + [char]0x0131 + [char]0x015F + [char]0x0131 + 'yor (sa' + [char]0x011F + 'l' + [char]0x0131 + 'kl' + [char]0x0131 + ')'
    Assert-PaSelf ((ConvertTo-PaEnglishState $tr) -eq 'Running') 'the Turkish Running label'
    Assert-PaSelf ((ConvertTo-PaEnglishState ('En marche (' + [char]0x00E9 + 'tat OK)')) -eq 'Running') 'the French Running label'
    Assert-PaSelf ((ConvertTo-PaEnglishState 'Running (health OK)') -eq 'Running') 'the English Running label'
    Assert-PaSelf ((ConvertTo-PaEnglishState 'Durdu') -eq 'Stopped') 'the Turkish Stopped label'
    Assert-PaSelf ((Get-PaPracticeFromLog $log) -eq 'off') 'practice from log'
    Assert-PaSelf ((Get-PaStateFromLog ($log + "2027-03-21 10:05:00.000 I/App: log opened (pid 2)`n")) -eq '') 'a new process has no state yet'
    Assert-PaSelf (@(Select-PaLogSince $log '2027-03-21 10:00:09').Count -eq 2) 'log since a stamp'
    $alarm = "nowRTC=1 nowELAPSED=1000000`n RTC_WAKEUP #0: Alarm{ab12 type 0 origWhen 5 whenElapsed 1600000 $PaOwnerPackage}`n"
    Assert-PaSelf (Test-PaAlarmDue $alarm $PaOwnerPackage 30) 'alarm due in 10 minutes'
    $far = $alarm.Replace('whenElapsed 1600000', 'whenElapsed 9000000')
    Assert-PaSelf (-not (Test-PaAlarmDue $far $PaOwnerPackage 30)) 'alarm far away'
    Assert-PaSelf (Test-PaAlarmDue ("RTC #0: Alarm{ab12 type 0 $PaOwnerPackage}`nnowELAPSED=5") $PaOwnerPackage 30) 'an alarm entry that cannot be parsed counts as due'
    $others = "nowRTC=1 nowELAPSED=1000000`n RTC_WAKEUP #0: Alarm{cd34 type 0 origWhen 5 whenElapsed 1100000 com.example.other}`n"
    Assert-PaSelf (-not (Test-PaAlarmDue ($others + "Stats: $PaOwnerPackage +5s") $PaOwnerPackage 30)) 'the package in the statistics only is not an alarm'
    Assert-PaSelf (-not (Test-PaAlarmDue $others $PaOwnerPackage 30)) 'a parsable listing with other packages only has no alarm of ours'
    Assert-PaSelf (Test-PaAlarmDue 'nothing here' $PaOwnerPackage 30) 'an unparsable listing counts as due'
    Assert-PaSelf (Test-PaAlarmDue '' $PaOwnerPackage 30) 'an empty listing counts as due'
    Assert-PaSelf (Test-PaOwnerTypeAllowed 'chat.input' 'hello there') 'owner may type plain text'
    Assert-PaSelf (Test-PaOwnerTypeAllowed 'chat.input' 'LONG 2000') 'owner may type LONG'
    Assert-PaSelf (-not (Test-PaOwnerTypeAllowed 'chat.input' 'please MEMORY now')) 'owner may not type MEMORY'
    Assert-PaSelf (-not (Test-PaOwnerTypeAllowed 'chat.input' 'FORGE')) 'owner may not type FORGE'
    Assert-PaSelf (Test-PaOwnerTypeAllowed 'terminal.input' 'hermes --version') 'terminal exact text'
    Assert-PaSelf (-not (Test-PaOwnerTypeAllowed 'terminal.input' 'ls')) 'terminal other text'

    # the guard refuses the owner package before any adb call
    $refused = $false
    try { Assert-PaTwinTarget $PaOwnerPackage } catch { $refused = $true }
    Assert-PaSelf $refused 'Assert-PaTwinTarget refuses the owner package'
    $script:Pa.Package = $PaOwnerPackage
    foreach ($op in $script:PaTwinOps) {
        $fn = 'Invoke-PaOp' + ($op.Substring(0, 1).ToUpperInvariant() + $op.Substring(1))
        $threw = $false
        try { & $fn ([pscustomobject]@{ args = @('devices'); spec = 'clear' }) } catch { $threw = $_.Exception.Message.StartsWith('refused:') }
        Assert-PaSelf $threw ("op $op refuses the owner package")
    }
    $script:Pa.Package = $PaTwinPackage
    $script:Pa.Owner = $true; $script:Pa.AllowSystemSettings = $false
    $threw = $false
    try { Set-PaSystemScales @{ font_scale = '2.0' } } catch { $threw = $true }
    Assert-PaSelf $threw 'system scales refused on the owner package without -AllowSystemSettings'
    $script:Pa.Owner = $false

    Invoke-PaGuardSelfTest

    # frame statistics
    $gfx = Read-PaTextFile (Join-Path (Join-Path $root 'fixtures') 'gfxinfo-sample.txt')
    $frames = ConvertFrom-PaFrameStats $gfx
    Assert-PaSelf ($frames.Count -eq 10) ('ten unique frames (flag rows skipped, overlap merged), got ' + $frames.Count)
    $sum = Get-PaFrameSummary ([double[]]@($frames.Values))
    Assert-PaSelf ($sum.Within8_3Pct -eq 60.0) ('60 % within 8.3, got ' + $sum.Within8_3Pct)
    Assert-PaSelf ($sum.P50 -eq 8.0) ('p50 is 8.0, got ' + $sum.P50)
    Assert-PaSelf ($sum.P90 -eq 18.0) ('p90 is 18.0, got ' + $sum.P90)
    Assert-PaSelf ($sum.P95 -eq 40.0 -and $sum.P99 -eq 40.0 -and $sum.Worst -eq 40.0) 'p95, p99 and worst are 40'
    Assert-PaSelf ($sum.Over16_7 -eq 2 -and $sum.Over33 -eq 1) 'over 16.7 and over 33'

    # JSON round trip of a step with unicode and quotes
    $step = ConvertFrom-Json -InputObject '{"op":"tap","text":"a \"b\" \u00e7"}'
    Assert-PaSelf ((Expand-PaStep $step).text -eq 'a "b" ' + [char]0x00e7) 'a step survives the placeholder round trip'
}
