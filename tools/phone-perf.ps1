<#
.SYNOPSIS
  Smoothness, cold start and memory numbers from the phone (plan features 0.2 and 0.12). Windows PowerShell 5.1 and pwsh 7.

.DESCRIPTION
  Run it:
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\phone-perf.ps1 [switches]

  Scenarios are the files of tools\scenarios\perf (P1 Home scroll, P4 a 2,000-character answer streaming, P5 transitions, P6 cold start,
  P7 Home idle; P2 and P3 need the seeding link of the twin and are written later). Frames come from "dumpsys gfxinfo <pkg> framestats",
  polled every 400 ms and merged by IntendedVsync (the buffer holds only 120 frames). A frame is "within" when it took 8.3 ms or less
  (the phone draws at 120 Hz).

  -Mode Smooth|ColdStart|Memory|All   default Smooth (3 repeats; cold start 7)
  -Scenario P1,P5                      only these ids
  -Owner                               measure the owner's package: only P1, P5, P6, P7 and one practice chat for P4 (never P2, P3)
  -AllowSystemSettings                 lets the run set the animation scales to 1 and the font scale to 1.0 (restored afterwards);
                                       only the lead passes it; without it the scales stay as the owner has them
  -Label name                          folder under dist\perf (default <versionName>-<debug|optimised>)
  -Repeat n                            repeats per scenario
  -Report dirA,dirB                    no phone: print markdown tables of two or more runs side by side
  -Markdown file                       write the -Report tables to this file
  -Enforce                             exit 1 when a budget is missed
  -LimitMb n                           with -Mode Memory: the room measured in Wave 0; prints the headroom (the plan wants 0.25 or more)
  -SelfTest                            no phone; parses the gfxinfo fixture, checks budgets and the line format; prints SELFTEST OK

  Exit codes: 0 after a run (budgets are reported), 1 only with -Enforce and a missed budget, 2 usage or environment,
  3 refused for safety (alarm due, practice mode not provable).
#>
[CmdletBinding()]
param(
    [ValidateSet('Smooth', 'ColdStart', 'Memory', 'All')] [string]$Mode = 'Smooth',
    [string[]]$Scenario = @(),
    [switch]$Owner,
    [switch]$AllowSystemSettings,
    [string]$Package = '',
    [string]$Serial = '',
    [string]$Label = '',
    [int]$Repeat = 0,
    [string]$Out = '',
    [string[]]$Report = @(),
    [string]$Markdown = '',
    [switch]$Enforce,
    [int]$LimitMb = 0,
    [switch]$SelfTest,
    [string]$Adb = ''
)

Set-StrictMode -Version 2
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'build-ids.ps1')
. (Join-Path $PSScriptRoot 'phone-lib.ps1')

$perfRoot = Join-Path (Get-PaScenarioRoot) 'perf'
$perfOut = Join-Path (Join-Path (Get-PaRepoRoot) 'dist') 'perf'

# ---- pure helpers (also used by -SelfTest and -Report) ------------------------------------------------------------
function Get-PerfId([string]$Name) {
    $i = $Name.IndexOf('-')
    if ($i -gt 0) { return $Name.Substring(0, $i) }
    return $Name
}

function Get-PerfRest([string]$Name) {
    $i = $Name.IndexOf('-')
    if ($i -gt 0) { return $Name.Substring($i + 1) }
    return ''
}

# Budget check: returns @{ Pass = bool; Kind = 'PASS'|'FAIL'|'NONE'; Detail = text }.
function Test-PerfBudget($Budget, $Sum) {
    $names = @(Get-PaPropNames $Budget)
    if ($names.Count -eq 0) { return @{ Pass = $true; Kind = 'NONE'; Detail = '' } }
    $miss = @()
    if (Test-PaHas $Budget 'within8_3Pct') { if ($Sum.Within8_3Pct -lt [double]$Budget.within8_3Pct) { $miss += ('within8.3 {0}% < {1}%' -f $Sum.Within8_3Pct, $Budget.within8_3Pct) } }
    if (Test-PaHas $Budget 'over33') { if ($Sum.Over33 -gt [int]$Budget.over33) { $miss += ('over33 {0} > {1}' -f $Sum.Over33, $Budget.over33) } }
    if (Test-PaHas $Budget 'over16_7') { if ($Sum.Over16_7 -gt [int]$Budget.over16_7) { $miss += ('over16.7 {0} > {1}' -f $Sum.Over16_7, $Budget.over16_7) } }
    if ($miss.Count -gt 0) { return @{ Pass = $false; Kind = 'FAIL'; Detail = ($miss -join '; ') } }
    return @{ Pass = $true; Kind = 'PASS'; Detail = '' }
}

function Format-PerfLine([string]$Name, $Sum, [string]$Verdict) {
    $inv = [System.Globalization.CultureInfo]::InvariantCulture
    return ([string]::Format($inv, '{0} {1} frames={2} within8.3={3:0.0}% p90={4:0.0} p95={5:0.0} p99={6:0.0} worst={7:0.0} over16.7={8} over33={9} budget={10}',
            (Get-PerfId $Name), (Get-PerfRest $Name), $Sum.Frames, $Sum.Within8_3Pct, $Sum.P90, $Sum.P95, $Sum.P99, $Sum.Worst, $Sum.Over16_7, $Sum.Over33, $Verdict))
}

function Get-Median([double[]]$Values) {
    if ($Values.Length -eq 0) { return 0.0 }
    $s = @($Values | Sort-Object)
    $mid = [int][Math]::Floor($s.Count / 2)
    if ($s.Count % 2 -eq 1) { return $s[$mid] }
    return (($s[$mid - 1] + $s[$mid]) / 2.0)
}

function Read-PerfRun([string]$Dir) {
    $d = $Dir
    if (-not (Test-Path -LiteralPath $d)) { $d = Join-Path $perfOut $Dir }
    if (-not (Test-Path -LiteralPath $d)) { throw ("no perf folder: " + $Dir) }
    $items = @()
    foreach ($f in (Get-ChildItem -LiteralPath $d -Filter 'P*.json' -File | Sort-Object Name)) { $items += (Read-PaJsonFile $f.FullName) }
    return [pscustomobject]@{ Label = (Split-Path -Leaf $d); Items = $items }
}

# Markdown tables of two or more runs side by side.
function Format-PerfReport($Runs) {
    $md = New-Object System.Text.StringBuilder
    $inv = [System.Globalization.CultureInfo]::InvariantCulture
    $ids = @{}
    foreach ($r in $Runs) { foreach ($i in $r.Items) { $ids[[string](Get-PaProp $i 'scenario' '')] = $true } }
    $names = @($ids.Keys | Sort-Object)
    [void]$md.AppendLine('## Smoothness')
    [void]$md.AppendLine('')
    $head = '| Scenario | Budget |'
    $sep = '|---|---|'
    foreach ($r in $Runs) { $head += (' {0} frames | within 8.3 % | p90 | p95 | p99 | worst | verdict |' -f $r.Label); $sep += '---|---|---|---|---|---|---|' }
    [void]$md.AppendLine($head); [void]$md.AppendLine($sep)
    foreach ($n in $names) {
        $row = '| ' + $n + ' |'
        $budgetText = ''
        foreach ($r in $Runs) { $it = $r.Items | Where-Object { (Get-PaProp $_ 'scenario' '') -eq $n } | Select-Object -First 1; if ($null -ne $it -and $budgetText -eq '') { $budgetText = (Get-PaProp $it 'budgetText' '') } }
        $row += ' ' + $budgetText + ' |'
        foreach ($r in $Runs) {
            $it = $r.Items | Where-Object { (Get-PaProp $_ 'scenario' '') -eq $n } | Select-Object -First 1
            if ($null -eq $it) { $row += ' - | - | - | - | - | - | - |'; continue }
            $kind = [string](Get-PaProp $it 'kind' 'frames')
            if ($kind -eq 'coldstart') {
                $row += [string]::Format($inv, ' median {0} ms | runs {1} | | | | | {2} |', (Get-PaProp $it 'totalMsMedian' 0), (Get-PaProp $it 'runs' 0), (Get-PaProp $it 'verdict' ''))
            } else {
                $row += [string]::Format($inv, ' {0} | {1} | {2} | {3} | {4} | {5} | {6} |', (Get-PaProp $it 'frames' 0), (Get-PaProp $it 'within8_3Pct' 0), (Get-PaProp $it 'p90' 0), (Get-PaProp $it 'p95' 0), (Get-PaProp $it 'p99' 0), (Get-PaProp $it 'worstMs' 0), (Get-PaProp $it 'verdict' ''))
            }
        }
        [void]$md.AppendLine($row)
    }
    return $md.ToString()
}

# Memory of the app now: PSS of the main process and the resident size of every process of the app's uid (the guest runs under proot as
# children of the app). The numbers come from dumpsys meminfo, ps and /proc/meminfo; the limiter text is stored as it is or "unavailable".
function Get-PerfMemory([string]$Pkg) {
    $mi = (Invoke-PaShell ("dumpsys meminfo {0}" -f $Pkg) 60000).Out
    $pss = 0.0
    $m = [regex]::Match($mi, 'TOTAL PSS:\s+(\d+)')
    if (-not $m.Success) { $m = [regex]::Match($mi, '(?m)^\s*TOTAL\s+(\d+)') }
    if ($m.Success) { $pss = [double]$m.Groups[1].Value }
    $java = 0.0; $m = [regex]::Match($mi, 'Java Heap:\s+(\d+)'); if ($m.Success) { $java = [double]$m.Groups[1].Value }
    $native = 0.0; $m = [regex]::Match($mi, 'Native Heap:\s+(\d+)'); if ($m.Success) { $native = [double]$m.Groups[1].Value }
    $gfx = 0.0; $m = [regex]::Match($mi, 'Graphics:\s+(\d+)'); if ($m.Success) { $gfx = [double]$m.Groups[1].Value }
    $uidRss = 0.0; $mainRss = 0.0
    $pk = (Invoke-PaShell ("dumpsys package {0}" -f $Pkg) 40000).Out
    $m = [regex]::Match($pk, 'userId=(\d+)')
    if ($m.Success) {
        $user = 'u0_a' + ([int]$m.Groups[1].Value - 10000)
        foreach ($l in ((Invoke-PaShell 'ps -A -o USER,PID,RSS,NAME' 40000).Out -csplit "`n")) {
            $f = @($l.Trim() -csplit '\s+')
            if ($f.Count -ge 4 -and $f[0] -eq $user -and $f[2] -cmatch '^\d+$') {
                $uidRss += [double]$f[2]
                if ($f[3] -eq $Pkg) { $mainRss += [double]$f[2] }
            }
        }
    }
    $avail = 0.0
    $m = [regex]::Match((Invoke-PaShell 'cat /proc/meminfo' 20000).Out, 'MemAvailable:\s+(\d+)'); if ($m.Success) { $avail = [double]$m.Groups[1].Value }
    $lim = (Invoke-PaShell 'am memory-limiter status' 20000).Text.Trim()
    if ($lim -eq '' -or $lim -cmatch 'Unknown command|Exception|Error') { $lim = 'unavailable' }
    return [pscustomobject]@{ PssKb = $pss; JavaKb = $java; NativeKb = $native; GraphicsKb = $gfx; UidRssKb = $uidRss; MainRssKb = $mainRss; MemAvailableKb = $avail; Limiter = $lim }
}

# ---- self test --------------------------------------------------------------------------------------------------
if ($SelfTest) {
    try {
        Invoke-PaLibSelfTest
        $gfx = Read-PaTextFile (Join-Path (Join-Path (Get-PaScenarioRoot) 'fixtures') 'gfxinfo-sample.txt')
        $sum = Get-PaFrameSummary ([double[]]@((ConvertFrom-PaFrameStats $gfx).Values))
        $line = Format-PerfLine 'P3-chat200-scroll' $sum 'FAIL'
        $want = 'P3 chat200-scroll frames=10 within8.3=60.0% p90=18.0 p95=40.0 p99=40.0 worst=40.0 over16.7=2 over33=1 budget=FAIL'
        if ($line -ne $want) { throw ("SELFTEST FAIL: line format: " + $line) }
        $b = Test-PerfBudget ([pscustomobject]@{ within8_3Pct = 95; over33 = 0 }) $sum
        if ($b.Pass -or $b.Kind -ne 'FAIL') { throw 'SELFTEST FAIL: a missed budget must fail' }
        $b = Test-PerfBudget ([pscustomobject]@{ within8_3Pct = 50; over33 = 1 }) $sum
        if (-not $b.Pass) { throw 'SELFTEST FAIL: a met budget must pass' }
        $b = Test-PerfBudget ([pscustomobject]@{}) $sum
        if ($b.Kind -ne 'NONE') { throw 'SELFTEST FAIL: an empty budget is NONE' }
        if ((Get-Median ([double[]]@(5, 1, 9))) -ne 5) { throw 'SELFTEST FAIL: median' }
        $files = @(Get-ChildItem -LiteralPath $perfRoot -Filter 'P*.json' -File)
        if ($files.Count -lt 5) { throw 'SELFTEST FAIL: fewer than 5 perf scenario files' }
        foreach ($f in $files) {
            $j = Read-PaJsonFile $f.FullName
            $bad = @(Test-PaScenarioShape $j $f.Name)
            if ($bad.Count -gt 0) { throw ("SELFTEST FAIL: " + $f.Name + ": " + $bad[0]) }
            if (-not (Test-PaHas $j 'measure')) { throw ("SELFTEST FAIL: " + $f.Name + ": no measure block") }
        }
        $md = Format-PerfReport @([pscustomobject]@{ Label = 'a'; Items = @([pscustomobject]@{ scenario = 'P1-home-scroll'; kind = 'frames'; frames = 10; within8_3Pct = 60.0; p90 = 18.0; p95 = 40.0; p99 = 40.0; worstMs = 40.0; verdict = 'FAIL'; budgetText = '95 %' }) })
        if ($md -cnotmatch 'P1-home-scroll') { throw 'SELFTEST FAIL: report table' }
        Write-Host 'SELFTEST OK'
        exit 0
    } catch {
        Write-Host $_.Exception.Message -ForegroundColor Red
        exit 1
    }
}

# ---- report mode (no phone) ---------------------------------------------------------------------------------------
if ($Report.Count -gt 0) {
    $dirs = @($Report | ForEach-Object { $_ -csplit ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
    $runs = @()
    foreach ($d in $dirs) { $runs += (Read-PerfRun $d) }
    $text = Format-PerfReport $runs
    if ($Markdown -ne '') { Write-PaTextFile $Markdown $text; Write-Host ('wrote ' + $Markdown) } else { Write-Host $text }
    exit 0
}

# ---- target and environment ---------------------------------------------------------------------------------------
$isOwner = [bool]$Owner
$target = $PaTwinPackage
if ($isOwner) { $target = $PaOwnerPackage }
if ($Package -ne '') { $target = $Package }
if ((Test-PaOwnerPackage $target) -and -not $isOwner) { Stop-Pa 'the owner package needs the explicit -Owner switch' 2 }
if ($isOwner -and -not (Test-PaOwnerPackage $target)) { Stop-Pa '-Owner and -Package point at different packages' 2 }

$adbPath = Find-PaAdb $Adb
if ($adbPath -eq '') { Stop-Pa 'adb was not found; pass -Adb <path>' 2 }
Initialize-PaRun @{ Adb = $adbPath; Package = $target; Owner = $isOwner; AllowSystemSettings = [bool]$AllowSystemSettings; NoA11y = $true; NoCut = $true; Vars = @{} }
$script:Pa.ChecksOn = $false
$why = Select-PaDevice $Serial
if ($why -ne '') { Stop-Pa $why 2 }
if (-not (Test-PaPackageInstalled $target)) { Stop-Pa ("package $target is not installed on the phone") 2 }
$why = Test-PaScreenReady
if ($why -ne '') { Stop-Pa $why 2 }

$pkgDump = (Invoke-PaShell ("dumpsys package {0}" -f $target) 40000).Out
$versionName = 'unknown'
$m = [regex]::Match($pkgDump, 'versionName=(\S+)'); if ($m.Success) { $versionName = $m.Groups[1].Value }
$debuggable = ($pkgDump -cmatch 'pkgFlags=\[[^\]]*DEBUGGABLE')
$compile = 'unknown'
$m = [regex]::Match($pkgDump, 'status=([a-z-]+)\]'); if ($m.Success) { $compile = $m.Groups[1].Value }
$buildKind = 'optimised'
if ($debuggable) { $buildKind = 'debug' }
if ($Label -eq '') { $Label = $versionName + '-' + $buildKind }
$display = (Invoke-PaShell 'dumpsys display' 40000).Out
$hz = ''
$m = [regex]::Match($display, 'fps=([0-9.]+)'); if ($m.Success) { $hz = $m.Groups[1].Value }
$thermal = 'unknown'
$m = [regex]::Match((Invoke-PaShell 'dumpsys thermalservice' 40000).Out, 'Thermal Status: (\d+)'); if ($m.Success) { $thermal = $m.Groups[1].Value }
if ((Invoke-PaShell 'settings get global low_power').Out.Trim() -eq '1') { Write-Host 'NOTE battery saver is on: the numbers are not comparable' }

if ($Out -eq '') { $Out = Join-Path $perfOut $Label }
New-PaDir $Out
$workDir = Join-Path $Out 'runs'

# ---- which scenarios ------------------------------------------------------------------------------------------------
$wantIds = @()
foreach ($s in $Scenario) { foreach ($p in ($s -csplit ',')) { if ($p.Trim() -ne '') { $wantIds += $p.Trim() } } }
$all = @()
foreach ($f in (Get-ChildItem -LiteralPath $perfRoot -Filter 'P*.json' -File | Sort-Object Name)) {
    $j = Read-PaJsonFile $f.FullName
    $all += [pscustomobject]@{ Id = (Get-PerfId $j.name); Scn = $j }
}
foreach ($w in $wantIds) {
    if (@($all | Where-Object { $_.Id -eq $w }).Count -eq 0) { Write-Host ('SKIP {0} (no scenario file yet: written after the twin installs)' -f $w) }
}
function Test-ModeFits($Scn, [string]$M) {
    $kind = [string](Get-PaProp (Get-PaProp $Scn 'measure' $null) 'kind' 'frames')
    switch ($M) {
        'All' { return $true }
        'Smooth' { return ($kind -ne 'coldstart') }
        'ColdStart' { return ($kind -eq 'coldstart') }
        'Memory' { return $false }
    }
    return $false
}
# an explicit -Scenario list wins over -Mode
if ($wantIds.Count -gt 0) { $chosen = @($all | Where-Object { $wantIds -contains $_.Id }) }
else { $chosen = @($all | Where-Object { Test-ModeFits $_.Scn $Mode }) }
$ownerIds = @('P1', 'P4', 'P5', 'P6', 'P7')
$twinOnlyIds = @('P2', 'P3')

$summary = New-Object System.Collections.ArrayList
$anyMiss = $false
$refused = $false
$practiceOn = $false

try {
    Set-PaSystemScales -Restore
    if (Test-PaSystemSettingsAllowed) { Set-PaPerfScales }
    else { Write-Host 'NOTE animation scales and font scale are left as they are (owner package without -AllowSystemSettings)' }

    foreach ($c in $chosen) {
        $scn = $c.Scn
        $name = [string]$scn.name
        $measure = Get-PaProp $scn 'measure' $null
        $kind = [string](Get-PaProp $measure 'kind' 'frames')
        $skip = ''
        if ($isOwner -and $ownerIds -notcontains $c.Id) { $skip = 'not run on the owner package' }
        if ($target -ne $PaTwinPackage -and $twinOnlyIds -contains $c.Id) { $skip = 'needs the twin' }
        if ($skip -ne '') { Write-Host ('SKIP {0} ({1})' -f $name, $skip); continue }

        $runs = $Repeat
        if ($runs -le 0) { if ($kind -eq 'coldstart') { $runs = 7 } else { $runs = 3 } }
        $budget = Get-PaProp $measure 'budget' ([pscustomobject]@{})
        $budgetText = ((Get-PaPropNames $budget | ForEach-Object { '{0}={1}' -f $_, (Get-PaProp $budget $_) }) -join ' ')

        if ($kind -eq 'coldstart') {
            $comp = Get-PaComponent $target $PaMainActivity
            $totals = New-Object System.Collections.ArrayList
            $waits = New-Object System.Collections.ArrayList
            for ($i = 0; $i -lt $runs; $i++) {
                Invoke-PaShell ("am force-stop {0}" -f $target) | Out-Null
                Start-Sleep -Milliseconds 1500
                $r = Invoke-PaShell ("am start -W -n {0}" -f $comp) 60000
                $t = [regex]::Match($r.Out, 'TotalTime:\s*(\d+)'); $w = [regex]::Match($r.Out, 'WaitTime:\s*(\d+)')
                if ($t.Success) { [void]$totals.Add([double]$t.Groups[1].Value) }
                if ($w.Success) { [void]$waits.Add([double]$w.Groups[1].Value) }
                Start-Sleep -Milliseconds 1500
            }
            $med = Get-Median ([double[]]@($totals))
            $medW = Get-Median ([double[]]@($waits))
            $verdict = 'NONE'
            if (Test-PaHas $budget 'coldMs') { if ($med -le [double]$budget.coldMs -and $totals.Count -gt 0) { $verdict = 'PASS' } else { $verdict = 'FAIL'; $anyMiss = $true } }
            $item = [ordered]@{
                schema = 1; label = $Label; package = $target; versionName = $versionName; debuggable = $debuggable; compileFilter = $compile
                refreshHz = $hz; thermal = $thermal; scenario = $name; kind = 'coldstart'; runs = $totals.Count
                totalMsMedian = $med; waitMsMedian = $medW; totalMs = @($totals); budget = $budget; budgetText = $budgetText
                verdict = $verdict; pass = ($verdict -ne 'FAIL'); polls = 0
            }
            Write-PaJsonFile (Join-Path $Out ($name + '.json')) $item
            [void]$summary.Add($item)
            Write-Host ('{0} {1} runs={2} total={3} wait={4} budget={5}' -f (Get-PerfId $name), (Get-PerfRest $name), $totals.Count, [int]$med, [int]$medW, $verdict)
            continue
        }

        # frame-based scenario: the sampler collects over every repeat
        $hasMeasure = $false
        foreach ($s in @(Get-PaProp $scn 'steps' @())) { if ((Get-PaProp $s 'op' '') -eq 'measure') { $hasMeasure = $true } }
        $needsPractice = [bool](Get-PaProp $scn 'practice' $false)
        if ($needsPractice) {
            # the intent is marked BEFORE the switch is touched: if anything after the tap throws, the finally still turns practice off
            $practiceOn = $true
            try { [void](Set-PaPracticeMode $true) } catch {
                Write-Host ('FAIL {0} practice mode: {1}' -f $name, $_.Exception.Message)
                if ($_.Exception.Message.StartsWith('refused:') -or $isOwner) { $refused = $true }
                continue
            }
        }
        $durations = New-Object System.Collections.ArrayList
        $polls = 0
        $failed = ''
        for ($i = 0; $i -lt $runs; $i++) {
            $sampler = New-PaSampler
            $sampler.Active = (-not $hasMeasure)
            $script:Pa.Sampler = $sampler
            $script:Pa.Hook = { Invoke-PaSamplerPoll }
            $script:Pa.TapCache = @{}
            if ($sampler.Active) { Invoke-PaSamplerReset }
            $res = Invoke-PaScenario $scn (Join-Path $workDir ('{0}-{1}' -f $name, $i))
            if ($sampler.Active) { Invoke-PaSamplerPoll; $sampler.Active = $false }
            $script:Pa.Hook = $null
            if ($res.Status -ne 'PASS') { $failed = $res.Detail; break }
            foreach ($k in $sampler.Frames.Keys) { [void]$durations.Add([double]$sampler.Frames[$k]) }
            $polls += $sampler.Polls
        }
        $script:Pa.Sampler = $null
        if ($failed -ne '') { Write-Host ('FAIL {0} {1}' -f $name, $failed); continue }
        $sum = Get-PaFrameSummary ([double[]]@($durations))
        $b = Test-PerfBudget $budget $sum
        if ($kind -eq 'idle') { $b = @{ Pass = $true; Kind = 'NONE'; Detail = '' } }
        if (-not $b.Pass) { $anyMiss = $true }
        $item = [ordered]@{
            schema = 1; label = $Label; package = $target; versionName = $versionName; debuggable = $debuggable; compileFilter = $compile
            refreshHz = $hz; thermal = $thermal; scenario = $name; kind = $kind; runs = $runs; frames = $sum.Frames
            within8_3Pct = $sum.Within8_3Pct; p50 = $sum.P50; p90 = $sum.P90; p95 = $sum.P95; p99 = $sum.P99; worstMs = $sum.Worst
            over16_7 = $sum.Over16_7; over33 = $sum.Over33; budget = $budget; budgetText = $budgetText
            verdict = $b.Kind; pass = $b.Pass; polls = $polls
        }
        Write-PaJsonFile (Join-Path $Out ($name + '.json')) $item
        [void]$summary.Add($item)
        Write-Host (Format-PerfLine $name $sum $b.Kind)
        if ($b.Detail -ne '') { Write-Host ('   missed: ' + $b.Detail) }
    }

    if ($Mode -eq 'Memory' -or $Mode -eq 'All') {
        $mem = Get-PerfMemory $target
        $peak = $mem.PssKb + [Math]::Max(0, ($mem.UidRssKb - $mem.MainRssKb))
        $head = $null
        if ($LimitMb -gt 0) { $head = [Math]::Round(1.0 - ($peak / 1024.0) / $LimitMb, 3) }
        $obj = [ordered]@{ schema = 1; label = $Label; package = $target; phase = 'current'; pssKb = $mem.PssKb; javaKb = $mem.JavaKb; nativeKb = $mem.NativeKb
            graphicsKb = $mem.GraphicsKb; uidRssKb = $mem.UidRssKb; mainRssKb = $mem.MainRssKb; memAvailableKb = $mem.MemAvailableKb; limiter = $mem.Limiter
            peakKb = $peak; limitMb = $LimitMb; headroom = $head }
        Write-PaJsonFile (Join-Path $Out 'memory.json') $obj
        Write-Host ('MEMORY pss={0}MB peak={1}MB headroom={2}' -f [int]($mem.PssKb / 1024), [int]($peak / 1024), $head)
    }
} finally {
    $script:Pa.Hook = $null
    try { Set-PaSystemScales -Restore } catch { Write-Host ('WARNING could not restore the system scales: ' + $_.Exception.Message) }
    if ($practiceOn) {
        try { [void](Set-PaPracticeMode $false) } catch { Write-Host ('WARNING practice mode may still be on: ' + $_.Exception.Message); $refused = $true }
    }
}

Write-PaJsonFile (Join-Path $Out 'summary.json') ([ordered]@{ schema = 1; label = $Label; package = $target; versionName = $versionName; debuggable = $debuggable; compileFilter = $compile; refreshHz = $hz; thermal = $thermal; scenarios = @($summary) })
Write-Host ('REPORT ' + $Out)
if ($refused) { exit 3 }
if ($Enforce -and $anyMiss) { exit 1 }
exit 0
