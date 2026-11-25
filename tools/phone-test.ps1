<#
.SYNOPSIS
  The phone robot: runs scenario files (tools/scenarios) against the app on a connected phone. Windows PowerShell 5.1 and pwsh 7.

.DESCRIPTION
  Run it (script execution is disabled on this PC, so always):
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\phone-test.ps1 [switches]

  Target. Default: the TEST TWIN (dev.pocketagent.app.debug). The owner's package needs -Owner, and then only scenarios marked
  "safe": true run (they read, navigate and take pictures; none wipes, seeds, writes a preference, kills the guest, installs or
  picks a file). Every destructive op guards itself (Assert-PaTwinTarget in phone-lib.ps1).

  -Suite core|smoke|slow|all   which scenarios (flag in the file). Default core; with -Owner default smoke.
  -Scenario a,b                run exactly these (names without .json)
  -Owner                       target dev.pocketagent.app, safe scenarios only
  -AllowSystemSettings         lets a run on the owner package change animation scales and the font scale (restored afterwards).
                               Only the lead passes it. Without it the font 2.0 cut check is skipped on the owner package.
  -Package id  -Serial id      pick another package or phone
  -Out dir                     report folder (default dist\phone-test\<versionName>\<yyyyMMdd-HHmmss>)
  -Var k=v[,k2=v2]             values for {{var:k}} in the scenarios (lang=en-XZ, marker=<regex>)
  -NoA11y -NoCut               switch an automatic check off
  -StopOnFail                  stop at the first failing scenario
  -List                        print the scenarios of the suite and exit
  -SelfTest                    no phone: checks parsers, selectors, checks, guards, scenario files; prints SELFTEST OK

  Output lines: PASS name 41.2s | FAIL name step 7 ... | SKIP name (reason) | A11Y ... | CUT ... | SUMMARY pass=n fail=n skip=n a11y=n cut=n
  Exit codes: 0 all passed, 1 a FAIL or an unlisted finding, 2 usage or environment (no phone, package missing, screen locked),
  3 nothing ran, or refused for safety (alarm due, practice mode not provable, or the window in front is not the app under test).

  Focus guard: before EVERY tap, swipe, key and typed text the robot reads which window has the focus (phone-lib.ps1, Invoke-PaInput) and
  sends nothing unless it belongs to the package under test (or a system surface the op is allowed to meet). Otherwise the scenario fails
  naming the window, a picture is taken and the run stops with exit 3; only a link step that brings the app back lets input go on.
#>
[CmdletBinding()]
param(
    [ValidateSet('core', 'smoke', 'slow', 'all')] [string]$Suite = '',
    [string[]]$Scenario = @(),
    [switch]$Owner,
    [switch]$AllowSystemSettings,
    [string]$Package = '',
    [string]$Serial = '',
    [string]$Out = '',
    [string[]]$Var = @(),
    [switch]$NoA11y,
    [switch]$NoCut,
    [switch]$StopOnFail,
    [switch]$List,
    [switch]$SelfTest,
    [string]$Adb = ''
)

Set-StrictMode -Version 2
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'build-ids.ps1')
. (Join-Path $PSScriptRoot 'phone-lib.ps1')

# ---- self test -----------------------------------------------------------------------------------------------
if ($SelfTest) {
    try {
        Invoke-PaLibSelfTest
        # the scenario files: shape, vocabulary, safe rules
        $files = @(Read-PaScenarios)
        if ($files.Count -lt 9) { throw ("SELFTEST FAIL: expected at least 9 scenario files, found " + $files.Count) }
        $ops = @($script:PaOps)
        foreach ($f in $files) {
            $bad = @(Test-PaScenarioShape $f.Scenario $f.File)
            if ($bad.Count -gt 0) { throw ("SELFTEST FAIL: " + $f.File + ": " + $bad[0]) }
            if ([bool](Get-PaProp $f.Scenario 'safe' $false)) {
                if ([bool](Get-PaProp $f.Scenario 'practice' $false)) { throw ("SELFTEST FAIL: " + $f.File + ": a safe scenario is flagged practice") }
                $all = @()
                foreach ($s in @(Get-PaProp $f.Scenario 'steps' @())) { $all += $s; foreach ($i in @(Get-PaProp $s 'steps' @())) { $all += $i } }
                foreach ($s in $all) {
                    $op = [string](Get-PaProp $s 'op' '')
                    if (@('wipe', 'seed', 'pref', 'killGuest', 'restore', 'install', 'adb', 'pick', 'scale', 'expectFile') -contains $op) {
                        throw ("SELFTEST FAIL: " + $f.File + ": a safe scenario uses op " + $op)
                    }
                    $dbg = Get-PaProp $s 'debug' $null
                    if ($null -ne $dbg -and ((Test-PaHas $dbg 'seed') -or (Test-PaHas $dbg 'nav'))) { throw ("SELFTEST FAIL: " + $f.File + ": a safe scenario sends the seed or nav extra") }
                }
            }
        }
        $routes = @(Read-PaRoutes)
        if ($routes.Count -lt 20) { throw 'SELFTEST FAIL: routes.json lists fewer than 20 routes' }
        Write-Host 'SELFTEST OK'
        exit 0
    } catch {
        Write-Host $_.Exception.Message -ForegroundColor Red
        exit 1
    }
}

# ---- scenarios of the suite ------------------------------------------------------------------------------------
$isOwner = [bool]$Owner
if ($Suite -eq '') { if ($isOwner) { $Suite = 'smoke' } else { $Suite = 'core' } }

$all = @(Read-PaScenarios)
function Test-InSuite($Scn, [string]$S) {
    $flags = @(Get-PaProp $Scn 'flags' @())
    switch ($S) {
        'all' { return $true }
        'core' { return (($flags -contains 'core') -and ($flags -notcontains 'slow')) }
        'smoke' { return ($flags -contains 'smoke') }
        'slow' { return ($flags -contains 'slow') }
    }
    return $false
}
if ($Scenario.Count -gt 0) {
    $wanted = @($Scenario | ForEach-Object { $_ -csplit ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
    $chosen = @($all | Where-Object { $wanted -contains (Get-PaProp $_.Scenario 'name' '') })
    $missing = @($wanted | Where-Object { $n = $_; @($all | Where-Object { (Get-PaProp $_.Scenario 'name' '') -eq $n }).Count -eq 0 })
    if ($missing.Count -gt 0) { Stop-Pa ('unknown scenario: ' + ($missing -join ', ')) 2 }
} else {
    $chosen = @($all | Where-Object { Test-InSuite $_.Scenario $Suite })
}

if ($List) {
    foreach ($c in $chosen) {
        $safe = ''
        if ([bool](Get-PaProp $c.Scenario 'safe' $false)) { $safe = ' [safe]' }
        Write-Host ('{0}{1}  {2}' -f (Get-PaProp $c.Scenario 'name' ''), $safe, (Get-PaProp $c.Scenario 'title' ''))
    }
    exit 0
}

# ---- target ----------------------------------------------------------------------------------------------------
$target = $PaTwinPackage
if ($isOwner) { $target = $PaOwnerPackage }
if ($Package -ne '') { $target = $Package }
if ((Test-PaOwnerPackage $target) -and -not $isOwner) { Stop-Pa 'the owner package needs the explicit -Owner switch' 2 }
if ($isOwner -and -not (Test-PaOwnerPackage $target)) { Stop-Pa '-Owner and -Package point at different packages' 2 }

$vars = @{}
foreach ($v in $Var) {
    foreach ($pair in ($v -csplit ',(?=[A-Za-z]+=)')) {
        $i = $pair.IndexOf('=')
        if ($i -le 0) { Stop-Pa ('bad -Var ' + $pair + ' (use name=value)') 2 }
        $vars[$pair.Substring(0, $i)] = $pair.Substring($i + 1)
    }
}

$adbPath = Find-PaAdb $Adb
if ($adbPath -eq '') { Stop-Pa 'adb was not found (PATH, ANDROID_HOME, %LOCALAPPDATA%\Android\Sdk\platform-tools); pass -Adb <path>' 2 }

Initialize-PaRun @{
    Adb = $adbPath; Package = $target; Owner = $isOwner; AllowSystemSettings = [bool]$AllowSystemSettings
    NoA11y = [bool]$NoA11y; NoCut = [bool]$NoCut; Vars = $vars
}
$why = Select-PaDevice $Serial
if ($why -ne '') { Stop-Pa $why 2 }
if (-not (Test-PaPackageInstalled $target)) { Stop-Pa ("package $target is not installed on the phone") 2 }
$why = Test-PaScreenReady
if ($why -ne '') { Stop-Pa $why 2 }

$versionName = Get-PaVersionName $target
if ($Out -eq '') {
    $Out = Join-Path (Join-Path (Join-Path (Join-Path (Get-PaRepoRoot) 'dist') 'phone-test') $versionName) (Get-Date).ToString('yyyyMMdd-HHmmss')
}
New-PaDir $Out

# On the twin the variant line must say twin before any scenario with a twin-only op runs; scenarios check it themselves as well.
$results = New-Object System.Collections.ArrayList
$ranAny = $false
$stop = $false
$refusedForSafety = $false

try {
    # a leftover backup of the system scales (an earlier run died): put the owner's values back first
    Set-PaSystemScales -Restore
    if (Test-PaSystemSettingsAllowed) {
        Set-PaFunctionalScales
        $script:Pa.FunctionalScales = $true
    } else {
        Write-Host 'NOTE animation scales are left as they are (owner package without -AllowSystemSettings); dumps on Home may need retries'
    }

    foreach ($c in $chosen) {
        if ($stop) { break }
        $scn = $c.Scenario
        $name = [string](Get-PaProp $scn 'name' '')
        $reason = ''
        $isSafe = [bool](Get-PaProp $scn 'safe' $false)
        if ($isOwner -and -not $isSafe) { $reason = 'not marked safe: refused on the owner package' }
        elseif ($target -ne $PaTwinPackage -and -not $isOwner -and -not $isSafe) { $reason = 'needs the twin' }
        if ($reason -eq '' -and $target -ne $PaTwinPackage) {
            $needsTwin = $false
            $stepsAll = @()
            foreach ($s in @(Get-PaProp $scn 'steps' @())) { $stepsAll += $s; foreach ($i in @(Get-PaProp $s 'steps' @())) { $stepsAll += $i } }
            foreach ($s in $stepsAll) { if ($script:PaTwinOps -contains [string](Get-PaProp $s 'op' '')) { $needsTwin = $true } }
            if ($needsTwin -or [bool](Get-PaProp $scn 'practice' $false)) { $reason = 'needs the twin' }
        }
        if ($reason -ne '') {
            [void]$results.Add([pscustomobject]@{ Name = $name; Status = 'SKIP'; Seconds = 0; Detail = $reason; Shot = '' })
            Write-Host ('SKIP {0} ({1})' -f $name, $reason)
            continue
        }
        $ranAny = $true
        if ([bool](Get-PaProp $scn 'practice' $false)) {
            try { Invoke-PaPracticeOn } catch {
                $msg = $_.Exception.Message
                [void]$results.Add([pscustomobject]@{ Name = $name; Status = 'FAIL'; Seconds = 0; Detail = ('practice mode: ' + $msg); Shot = '' })
                Write-Host ('FAIL {0} practice mode: {1}' -f $name, $msg)
                if ($msg.StartsWith('refused:')) { $refusedForSafety = $true }
                if ($StopOnFail) { $stop = $true }
                # the focus guard stopped the input: nothing else of this run sends any
                if ($script:Pa.FocusRefused) { $stop = $true; $refusedForSafety = $true }
                continue
            }
        }
        $r = Invoke-PaScenario $scn (Join-Path $Out $name)
        [void]$results.Add($r)
        if ($script:Pa.FocusRefused) { $stop = $true; $refusedForSafety = $true }
        if ($r.Status -eq 'PASS') {
            Write-Host ('PASS {0} {1}s' -f $r.Name, (Format-PaMs $r.Seconds))
            if ($r.Detail -ne '') { Write-Host ('NOTE {0}: {1}' -f $r.Name, $r.Detail) }
        } else {
            $shot = ''
            if ($r.Shot -ne '') { $shot = (' (shot {0})' -f $r.Shot) }
            Write-Host ('FAIL {0} {1}{2}' -f $r.Name, $r.Detail, $shot)
            if ($StopOnFail) { $stop = $true }
        }
    }
} finally {
    try { Set-PaSystemScales -Restore } catch { Write-Host ('WARNING could not restore the system scales: ' + $_.Exception.Message) }
    if ($script:Pa.PracticeTurnedOn) {
        # after a stop by the focus guard the app is first brought to the front by a link (no input); the switch is tapped only when it is there
        Restore-PaForeground
        try { [void](Set-PaPracticeMode $false) } catch { Write-Host ('WARNING practice mode may still be on: ' + $_.Exception.Message) }
    }
    try {
        if (-not $isOwner) { Write-PaTextFile (Join-Path $Out 'app.log') (Get-PaAppLog) }
    } catch { }
}

foreach ($l in $script:Pa.A11y) { Write-Host $l }
foreach ($l in $script:Pa.Cut) { Write-Host $l }
$pass = @($results | Where-Object { $_.Status -eq 'PASS' }).Count
$fail = @($results | Where-Object { $_.Status -eq 'FAIL' }).Count
$skip = @($results | Where-Object { $_.Status -eq 'SKIP' }).Count
Write-PaReport $Out $results $versionName $target $isOwner
Write-Host ('SUMMARY pass={0} fail={1} skip={2} a11y={3} cut={4}' -f $pass, $fail, $skip, $script:Pa.A11y.Count, $script:Pa.Cut.Count)
Write-Host ('REPORT ' + $Out)

if ($refusedForSafety) { exit 3 }
if (-not $ranAny) { exit 3 }
if ($fail -gt 0 -or $script:Pa.A11y.Count -gt 0 -or $script:Pa.Cut.Count -gt 0) { exit 1 }
exit 0
