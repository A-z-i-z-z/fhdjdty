<#
.SYNOPSIS
  The UI ratchet lint (plan feature 0.5): counts forbidden patterns in the Kotlin sources and fails when a count goes UP. Windows PowerShell 5.1 and pwsh 7.

.DESCRIPTION
  Run it from the repo root (script execution is disabled on this PC, so always):
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\lint-ui.ps1 [-Check] [-Update] [-Init] [-Json] [-SelfTest]
  CI runs the same file as:  pwsh -NoProfile -File tools/lint-ui.ps1 -Check

  -Check (default)  compare today's counts with tools/lint-ui-baseline.json. Exit 0: nothing is above the baseline (a lower count prints
                    "RATCHET rule 17 (baseline 21): run -Update"). Exit 1: a rule or a file is above ("OVER rule file n > m"). Exit 2: no baseline
                    (take one with -Init once, after the integration compile).
  -Update           lowers the baseline to today's counts; refuses (exit 1) when anything is above it. It never raises a number.
  -Init             writes the baseline when there is none (never overwrites one).
  -Json             prints today's counts as the baseline's JSON and exits 0.
  -SelfTest         checks the comment stripper, every rule on a snippet, the scopes and the ratchet logic; prints SELFTEST OK.

  The rules count matches in app\src\main\java\dev\pocketagent\app after block and line comments are removed (strings are kept).
  Keys in the baseline are paths below that folder with forward slashes, so the file reads the same on Windows and Linux.

    rawTween         tween( or tween<T>( and keyframes in ui/ except ui/theme/Motion.kt, Tokens.kt, PocketTheme.kt
    toast            Toast.makeText in ui/
    roundedCorner    RoundedCornerShape( in ui/ (same exceptions; Wave 0 only counts it)
    literalDuration  durationMillis = N, delay(N, delayMillis = N in ui/ (same exceptions; Dur., Loop., Flourish. never match)
    literalDp        N.dp in ui/ outside ui/theme/
    directText       stringResource(, pluralStringResource(, getQuantityString(, getString of a resource id, in ui/ service/ routines/ data/
                     hermes/ runtime/ voice/ except ui/text/ (the function itself calls stringResource)
    leftRight        AbsoluteAlignment., absolutePadding(, Arrangement.Left/Right, Alignment.*Left/*Right, TextAlign.Left/Right in ui/
    caseChange       .uppercase( .lowercase( .capitalize( and replaceFirstChar in ui/
    hapticDirect     performHapticFeedback( and HapticFeedbackType in ui/ except ui/theme/Haptics.kt
    sheetDirect      ModalBottomSheet( in ui/ except ui/components/PocketSheet.kt
#>
[CmdletBinding()]
param(
    [switch]$Check,
    [switch]$Update,
    [switch]$Init,
    [switch]$Json,
    [switch]$SelfTest
)

Set-StrictMode -Version 2
$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$SrcRoot = Join-Path $RepoRoot 'app/src/main/java/dev/pocketagent/app'
$BaselinePath = Join-Path $PSScriptRoot 'lint-ui-baseline.json'

$ThemeExempt = @('ui/theme/Motion.kt', 'ui/theme/Tokens.kt', 'ui/theme/PocketTheme.kt')

# Name, patterns (any of them), directories (prefixes, with a trailing slash), exempt files, exempt directories.
$Rules = @(
    @{ Name = 'rawTween'; Patterns = @('\btween\s*[<(]', '\bkeyframes\s*[<({]'); Dirs = @('ui/'); ExemptFiles = $ThemeExempt; ExemptDirs = @() },
    @{ Name = 'toast'; Patterns = @('Toast\.makeText'); Dirs = @('ui/'); ExemptFiles = @(); ExemptDirs = @() },
    @{ Name = 'roundedCorner'; Patterns = @('RoundedCornerShape\('); Dirs = @('ui/'); ExemptFiles = $ThemeExempt; ExemptDirs = @() },
    @{ Name = 'literalDuration'; Patterns = @('durationMillis\s*=\s*\d+', '\bdelay\(\s*\d+', 'delayMillis\s*=\s*\d+'); Dirs = @('ui/'); ExemptFiles = $ThemeExempt; ExemptDirs = @() },
    @{ Name = 'literalDp'; Patterns = @('\b\d+(\.\d+)?\.dp\b'); Dirs = @('ui/'); ExemptFiles = @(); ExemptDirs = @('ui/theme/') },
    @{ Name = 'directText'
       Patterns = @('\b(stringResource|pluralStringResource)\(', 'getQuantityString\(', 'getString\(\s*(R\.string\.|if\b|res\b|subtitle\b|$)|(^|[^.\w])getString\(')
       Dirs = @('ui/', 'service/', 'routines/', 'data/', 'hermes/', 'runtime/', 'voice/'); ExemptFiles = @(); ExemptDirs = @('ui/text/') },
    @{ Name = 'leftRight'
       Patterns = @('AbsoluteAlignment\.', 'absolutePadding\(', 'Arrangement\.(Left|Right)\b', 'Alignment\.(CenterLeft|CenterRight|TopLeft|TopRight|BottomLeft|BottomRight)\b', 'TextAlign\.(Left|Right)\b')
       Dirs = @('ui/'); ExemptFiles = @(); ExemptDirs = @() },
    @{ Name = 'caseChange'; Patterns = @('\.(uppercase|lowercase|capitalize)\(', '\breplaceFirstChar\b'); Dirs = @('ui/'); ExemptFiles = @(); ExemptDirs = @() },
    @{ Name = 'hapticDirect'; Patterns = @('performHapticFeedback\(', '\bHapticFeedbackType\b'); Dirs = @('ui/'); ExemptFiles = @('ui/theme/Haptics.kt'); ExemptDirs = @() },
    @{ Name = 'sheetDirect'; Patterns = @('ModalBottomSheet\('); Dirs = @('ui/'); ExemptFiles = @('ui/components/PocketSheet.kt'); ExemptDirs = @() }
)

# Strings are kept (they can contain two slashes), line comments and block comments go. One pass, so a slash-slash inside a string survives.
$CommentRx = New-Object System.Text.RegularExpressions.Regex('(?<keep>"""[\s\S]*?"""|"(?:\\.|[^"\\\r\n])*"|''(?:\\.|[^''\\\r\n])*'')|//[^\r\n]*|/\*[\s\S]*?\*/')

function Remove-KotlinComments([string]$Text) {
    return $CommentRx.Replace($Text, '${keep}')
}

function Test-InScope($Rule, [string]$Rel) {
    $in = $false
    foreach ($d in $Rule.Dirs) { if ($Rel.StartsWith($d)) { $in = $true; break } }
    if (-not $in) { return $false }
    if ($Rule.ExemptFiles -contains $Rel) { return $false }
    foreach ($d in $Rule.ExemptDirs) { if ($Rel.StartsWith($d)) { return $false } }
    return $true
}

function Get-RuleRegex($Rule) {
    $alts = @($Rule.Patterns | ForEach-Object { '(?:' + $_ + ')' })
    return (New-Object System.Text.RegularExpressions.Regex(($alts -join '|'), [System.Text.RegularExpressions.RegexOptions]::Multiline))
}

# Counts of one (already comment-free) text for one rule.
function Measure-LintText($Rule, [string]$CleanText) {
    return (Get-RuleRegex $Rule).Matches($CleanText).Count
}

# counts[rule][file] for every Kotlin file of the source folder. Files with no match are left out.
function Get-LintCounts {
    $counts = [ordered]@{}
    foreach ($r in $Rules) { $counts[$r.Name] = [ordered]@{} }
    $rx = @{}
    foreach ($r in $Rules) { $rx[$r.Name] = Get-RuleRegex $r }
    $files = @(Get-ChildItem -LiteralPath $SrcRoot -Recurse -File -Filter '*.kt' | Sort-Object FullName)
    foreach ($f in $files) {
        $rel = $f.FullName.Substring($SrcRoot.Length).TrimStart('\', '/').Replace('\', '/')
        $clean = $null
        foreach ($r in $Rules) {
            if (-not (Test-InScope $r $rel)) { continue }
            if ($null -eq $clean) { $clean = Remove-KotlinComments ([System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)) }
            $n = $rx[$r.Name].Matches($clean).Count
            if ($n -gt 0) { $counts[$r.Name][$rel] = $n }
        }
    }
    return $counts
}

function Get-RuleTotal($FileMap) {
    $t = 0
    foreach ($k in $FileMap.Keys) { $t += [int]$FileMap[$k] }
    return $t
}

# The baseline's JSON, written by hand so the file is stable and easy to diff.
function ConvertTo-LintJson($Counts, [string]$Date) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("{`n  `"schema`": 1,`n  `"generated`": `"$Date`",`n  `"rules`": {`n")
    $names = @($Counts.Keys)
    for ($i = 0; $i -lt $names.Count; $i++) {
        $n = $names[$i]
        $map = $Counts[$n]
        [void]$sb.Append("    `"$n`": {`"total`": $(Get-RuleTotal $map), `"files`": {")
        $keys = @($map.Keys | Sort-Object)
        if ($keys.Count -gt 0) {
            [void]$sb.Append("`n")
            for ($j = 0; $j -lt $keys.Count; $j++) {
                $k = $keys[$j]
                $sep = ','
                if ($j -eq $keys.Count - 1) { $sep = '' }
                [void]$sb.Append("      `"$k`": $($map[$k])$sep`n")
            }
            [void]$sb.Append('    ')
        }
        $end = ','
        if ($i -eq $names.Count - 1) { $end = '' }
        [void]$sb.Append("}}$end`n")
    }
    [void]$sb.Append("  }`n}`n")
    return $sb.ToString()
}

function Read-LintBaseline([string]$Text) {
    $j = ConvertFrom-Json -InputObject $Text
    $base = [ordered]@{}
    $rules = $j.rules
    foreach ($p in $rules.PSObject.Properties) {
        $files = [ordered]@{}
        $fm = $p.Value.files
        if ($null -ne $fm) { foreach ($q in $fm.PSObject.Properties) { $files[$q.Name] = [int]$q.Value } }
        $base[$p.Name] = $files
    }
    return $base
}

# Compares today with the baseline: Over (list of "OVER rule file n > m"), Ratchet (list of "RATCHET ..." lines).
function Compare-LintCounts($Counts, $Base) {
    $over = New-Object System.Collections.ArrayList
    $ratchet = New-Object System.Collections.ArrayList
    foreach ($rule in $Counts.Keys) {
        $now = $Counts[$rule]
        $was = [ordered]@{}
        if ($Base.Contains($rule)) { $was = $Base[$rule] }
        foreach ($f in $now.Keys) {
            $m = 0
            if ($was.Contains($f)) { $m = [int]$was[$f] }
            if ([int]$now[$f] -gt $m) { [void]$over.Add(("OVER {0} {1} {2} > {3}" -f $rule, $f, $now[$f], $m)) }
        }
        $tNow = Get-RuleTotal $now
        $tWas = Get-RuleTotal $was
        if ($tNow -lt $tWas) { [void]$ratchet.Add(("RATCHET {0} {1} (baseline {2}): run -Update" -f $rule, $tNow, $tWas)) }
    }
    return [pscustomobject]@{ Over = @($over); Ratchet = @($ratchet) }
}

# The lowered baseline: every file at the smaller of the two numbers.
function Get-LoweredBaseline($Counts, $Base) {
    $out = [ordered]@{}
    foreach ($rule in $Counts.Keys) {
        $map = [ordered]@{}
        foreach ($f in $Counts[$rule].Keys) {
            $v = [int]$Counts[$rule][$f]
            if ($Base.Contains($rule) -and $Base[$rule].Contains($f)) { $v = [Math]::Min($v, [int]$Base[$rule][$f]) }
            if ($v -gt 0) { $map[$f] = $v }
        }
        $out[$rule] = $map
    }
    return $out
}

# ---- self test --------------------------------------------------------------------------------------------------
function Assert-Lint([bool]$Cond, [string]$Msg) { if (-not $Cond) { throw ('SELFTEST FAIL: ' + $Msg) } }

function Get-RuleByName([string]$Name) { foreach ($r in $Rules) { if ($r.Name -eq $Name) { return $r } }; throw "no rule $Name" }

function Invoke-LintSelfTest {
    # the comment stripper
    $src = "val a = 1 // tween(5)`n/* Toast.makeText( */ val b = `"http://x`" /* nested /* no */`nval c = 'x'`n"
    $clean = Remove-KotlinComments $src
    Assert-Lint ($clean -notmatch 'tween') 'a line comment is removed'
    Assert-Lint ($clean -notmatch 'Toast') 'a block comment is removed'
    Assert-Lint ($clean.Contains('http://x')) 'a string with two slashes is kept'
    Assert-Lint ($clean.Contains("val c = 'x'")) 'a char literal is kept'

    $cases = @(
        @('rawTween', 'val s = tween(200) + keyframes { }', 2),
        @('rawTween', "val s = Motion.tween<Float>(1)`nval k = keyframes<Float> {}", 2),
        @('rawTween', '// tween(200) is old', 0),
        @('toast', 'Toast.makeText(ctx, "x", 0).show()', 1),
        @('toast', '/* Toast.makeText( */', 0),
        @('roundedCorner', 'shape = RoundedCornerShape(4.dp)', 1),
        @('literalDuration', 'tween(durationMillis = 300) and delay(50) and delayMillis = 20 but Dur.FAST and delay(Dur.TAP)', 3),
        @('literalDp', 'Modifier.padding(8.dp).size(0.5.dp).width(Space.m)', 2),
        @('directText', 'stringResource(R.string.a) pluralStringResource(R.plurals.b, 2)', 2),
        @('directText', 'ctx.getString(R.string.x) ctx.getString(id) ctx.getQuantityString(R.plurals.p, 1)', 2),
        @('directText', 'val t = getString(id)', 1),
        @('directText', "val t = res.getString(`n R.string.y)", 1),
        @('leftRight', 'Alignment.CenterLeft Arrangement.Right TextAlign.Left Alignment.CenterVertically Arrangement.Start', 3),
        @('leftRight', 'Modifier.absolutePadding(left = 4.dp)', 1),
        @('caseChange', 'a.uppercase() b.lowercase(Locale.ROOT) c.replaceFirstChar { it.uppercase() }', 4),
        @('hapticDirect', 'view.performHapticFeedback(HapticFeedbackType.LongPress)', 2),
        @('sheetDirect', 'ModalBottomSheet(onDismissRequest = {})', 1)
    )
    $i = 0
    foreach ($c in $cases) {
        $i++
        $n = Measure-LintText (Get-RuleByName $c[0]) (Remove-KotlinComments $c[1])
        Assert-Lint ($n -eq $c[2]) ("case {0}: rule {1} counted {2}, wanted {3}" -f $i, $c[0], $n, $c[2])
    }

    # scopes
    Assert-Lint (Test-InScope (Get-RuleByName 'rawTween') 'ui/chat/ChatScreen.kt') 'ui files are in scope'
    Assert-Lint (-not (Test-InScope (Get-RuleByName 'rawTween') 'ui/theme/Motion.kt')) 'Motion.kt is exempt'
    Assert-Lint (-not (Test-InScope (Get-RuleByName 'rawTween') 'data/ChatEngine.kt')) 'data is not in the ui rules'
    Assert-Lint (-not (Test-InScope (Get-RuleByName 'literalDp') 'ui/theme/Palettes.kt')) 'ui/theme is exempt for literalDp'
    Assert-Lint (Test-InScope (Get-RuleByName 'literalDp') 'ui/components/PixelPanel.kt') 'components count for literalDp'
    Assert-Lint (Test-InScope (Get-RuleByName 'directText') 'data/ChatEngine.kt') 'directText covers data'
    Assert-Lint (-not (Test-InScope (Get-RuleByName 'directText') 'ui/text/TextsCompose.kt')) 'ui/text is exempt for directText'
    Assert-Lint (-not (Test-InScope (Get-RuleByName 'sheetDirect') 'ui/components/PocketSheet.kt')) 'PocketSheet.kt is exempt'
    Assert-Lint (-not (Test-InScope (Get-RuleByName 'hapticDirect') 'ui/theme/Haptics.kt')) 'Haptics.kt is exempt'

    # the ratchet
    $base = [ordered]@{ rawTween = [ordered]@{ 'ui/a.kt' = 3; 'ui/b.kt' = 2 }; toast = [ordered]@{} }
    $same = [ordered]@{ rawTween = [ordered]@{ 'ui/a.kt' = 3; 'ui/b.kt' = 2 }; toast = [ordered]@{} }
    $cmp = Compare-LintCounts $same $base
    Assert-Lint ($cmp.Over.Count -eq 0 -and $cmp.Ratchet.Count -eq 0) 'equal counts are fine'
    $lower = [ordered]@{ rawTween = [ordered]@{ 'ui/a.kt' = 1; 'ui/b.kt' = 2 }; toast = [ordered]@{} }
    $cmp = Compare-LintCounts $lower $base
    Assert-Lint ($cmp.Over.Count -eq 0 -and $cmp.Ratchet.Count -eq 1) 'a lower count asks for -Update'
    Assert-Lint ($cmp.Ratchet[0] -eq 'RATCHET rawTween 3 (baseline 5): run -Update') ('ratchet line: ' + $cmp.Ratchet[0])
    $higher = [ordered]@{ rawTween = [ordered]@{ 'ui/a.kt' = 4; 'ui/b.kt' = 2 }; toast = [ordered]@{ 'ui/c.kt' = 1 } }
    $cmp = Compare-LintCounts $higher $base
    Assert-Lint ($cmp.Over.Count -eq 2) 'a higher count and a new file are over'
    Assert-Lint ($cmp.Over[0] -eq 'OVER rawTween ui/a.kt 4 > 3') ('over line: ' + $cmp.Over[0])
    $moved = [ordered]@{ rawTween = [ordered]@{ 'ui/a.kt' = 1; 'ui/b.kt' = 2; 'ui/d.kt' = 1 }; toast = [ordered]@{} }
    $cmp = Compare-LintCounts $moved $base
    Assert-Lint ($cmp.Over.Count -eq 1) 'a count that moves to a new file is over even when the total is lower'
    $lowered = Get-LoweredBaseline $lower $base
    Assert-Lint ([int]$lowered['rawTween']['ui/a.kt'] -eq 1) 'lowered baseline takes the smaller number'

    # the JSON round trip
    $text = ConvertTo-LintJson $lower '2026-11-25'
    $back = Read-LintBaseline $text
    Assert-Lint ([int]$back['rawTween']['ui/a.kt'] -eq 1 -and [int]$back['rawTween']['ui/b.kt'] -eq 2) 'baseline JSON round trip'
    Assert-Lint ($text.Contains('"total": 3')) 'baseline JSON carries the rule total'
    Write-Host 'SELFTEST OK'
}

if ($SelfTest) {
    try { Invoke-LintSelfTest; exit 0 } catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
}

# ---- run --------------------------------------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $SrcRoot)) { Write-Host ('lint-ui: source folder not found: ' + $SrcRoot); exit 1 }
$counts = Get-LintCounts

if ($Json) { Write-Host (ConvertTo-LintJson $counts (Get-Date).ToString('yyyy-MM-dd')); exit 0 }

if ($Init) {
    if (Test-Path -LiteralPath $BaselinePath) { Write-Host 'lint-ui: the baseline exists; -Init never overwrites it (use -Update to lower it)'; exit 0 }
    [System.IO.File]::WriteAllText($BaselinePath, (ConvertTo-LintJson $counts (Get-Date).ToString('yyyy-MM-dd')), (New-Object System.Text.UTF8Encoding($false)))
    foreach ($n in $counts.Keys) { Write-Host ('{0,-16} {1}' -f $n, (Get-RuleTotal $counts[$n])) }
    Write-Host ('lint-ui: baseline written to ' + $BaselinePath)
    exit 0
}

if (-not (Test-Path -LiteralPath $BaselinePath)) {
    Write-Host 'lint-ui: no baseline (tools/lint-ui-baseline.json). Take one once, after the integration compile: tools\lint-ui.ps1 -Init'
    exit 2
}
try { $base = Read-LintBaseline ([System.IO.File]::ReadAllText($BaselinePath, [System.Text.Encoding]::UTF8)) }
catch { Write-Host ('lint-ui: the baseline cannot be read: ' + $_.Exception.Message); exit 2 }

$cmp = Compare-LintCounts $counts $base

if ($Update) {
    if ($cmp.Over.Count -gt 0) {
        foreach ($l in $cmp.Over) { Write-Host $l }
        Write-Host 'lint-ui: -Update never raises a number; fix the lines above first'
        exit 1
    }
    $lowered = Get-LoweredBaseline $counts $base
    [System.IO.File]::WriteAllText($BaselinePath, (ConvertTo-LintJson $lowered (Get-Date).ToString('yyyy-MM-dd')), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ('lint-ui: baseline lowered (' + $cmp.Ratchet.Count + ' rule(s))')
    exit 0
}

foreach ($n in $counts.Keys) { Write-Host ('{0,-16} {1}' -f $n, (Get-RuleTotal $counts[$n])) }
foreach ($l in $cmp.Ratchet) { Write-Host $l }
if ($cmp.Over.Count -gt 0) {
    foreach ($l in $cmp.Over) { Write-Host $l }
    exit 1
}
Write-Host 'lint-ui: ok'
exit 0
