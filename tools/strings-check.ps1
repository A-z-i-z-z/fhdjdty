<#
.SYNOPSIS
  The strings check of the app (feature 0.11): are the texts in every shipped language complete, well
  formed and short enough? Also scans a uiautomator dump for text that did not come from the resources.

.DESCRIPTION
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\strings-check.ps1 [-ResDir d] [-Factor 1.4] [-WriteBaseline] [-FailOnUnused] [-Quiet]
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\strings-check.ps1 -ScanDump <dump.xml> [-Allow <regex-file>]

  There is no -Check switch: the plain run is the check.

  Exit: 0 pass (warnings allowed); 1 at least one error; 2 a ratchet got worse (more warnings than
  tools/strings-check.baseline.json); 3 bad call or unreadable file; 4 (-ScanDump) leaks found.

  Output lines are "ERROR <check> <file>:<key>[:<lang>] <detail>" and "WARN ...", the last line is
  "strings-check: languages=en,tr,fr keys=N errors=E warnings=W (baseline B)".

  What it checks (errors unless said):
    xml          every strings file is well formed and holds only string, plurals, item, string-array
    parity       the same files and keys in every shipped language (translatable="false" is exempt)
    placeholder  the placeholders of a translation equal the English ones (a plural form may drop the
                 number but never add one); an English text with two unnumbered placeholders
    blank        an empty text; a key defined twice in a language
    stretched    values-en-rXZ inside src/main (it must never ship); a stale generated language under
                 src/debug (keys, placeholders, or a text under 1.4 times the English length)
    protected    a name in assets/text/protected-keys.txt that does not exist
    note         a key of strings_engine.xml without a translator note
    length       a text longer than the room of its control (kinds: tools/strings-kinds.txt, or the
                 "| kind: chip" part of the note). English must fit the room, a translation
                 floor(room * Factor). Violations that were there when the baseline was written are
                 tolerated; a new one is an error.
  Warnings (the ratchet counts them): a key outside strings_engine.xml without a note; a key nothing
  refers to (an error with -FailOnUnused); a count noun after a bare number in a string.

  Runs on Windows PowerShell 5.1 and pwsh 7. This file is ASCII only on purpose (5.1 reads a file
  without a byte order mark as ANSI).
#>
[CmdletBinding()]
param(
    [string]$ResDir = '',
    [double]$Factor = 1.4,
    [switch]$WriteBaseline,
    [switch]$FailOnUnused,
    [switch]$Quiet,
    [string]$ScanDump = '',
    [string]$Allow = ''
)

$ErrorActionPreference = 'Stop'

$script:Root = Split-Path -Parent $PSScriptRoot
if (-not $ResDir) { $ResDir = Join-Path $script:Root 'app/src/main/res' }
$script:BaselinePath = Join-Path $PSScriptRoot 'strings-check.baseline.json'
$script:KindsPath = Join-Path $PSScriptRoot 'strings-kinds.txt'
$script:StretchTag = 'en-rXZ'
$script:StretchFactor = 1.4
$script:Rooms = @{ tab = 10; button = 22; chip = 18; title = 28; row = 40; pet = 80; widget = 14 }
$script:Units = @('min', 's', 'h', 'd', 'MB', 'KB', 'MiB', 'chars')
$script:PlaceholderPattern = '%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z%]'

$script:Errors = New-Object 'System.Collections.Generic.List[string]'
$script:Warns = New-Object 'System.Collections.Generic.List[string]'
$script:Infos = New-Object 'System.Collections.Generic.List[string]'

function Read-Utf8([string]$Path) {
    [System.IO.File]::ReadAllText($Path, (New-Object System.Text.UTF8Encoding($false)))
}

# Ordinal (code point) sort, the same on Windows PowerShell 5.1 and on pwsh 7 (Sort-Object is culture based and differs).
function Sort-Ordinal($Items) {
    if ($null -eq $Items) { return @() }
    $a = [string[]]@(foreach ($i in @($Items)) { [string]$i })
    [Array]::Sort($a, [StringComparer]::Ordinal)
    return $a
}

function Sort-OrdinalUnique($Items) {
    $sorted = @(Sort-Ordinal $Items)
    $res = New-Object 'System.Collections.Generic.List[string]'
    foreach ($x in $sorted) { if ($res.Count -eq 0 -or $res[$res.Count - 1] -cne $x) { $res.Add($x) } }
    return $res.ToArray()
}

function Format-Where([string]$File, [string]$Key, [string]$Lang) {
    $where = $File
    if ($Key) { $where = "${File}:${Key}" }
    if ($Lang) { $where = "${where}:${Lang}" }
    return $where
}

function Add-Err([string]$Check, [string]$File, [string]$Key, [string]$Lang, [string]$Detail) {
    $script:Errors.Add("ERROR $Check $(Format-Where $File $Key $Lang) $Detail")
}

function Add-Warn([string]$Check, [string]$File, [string]$Key, [string]$Lang, [string]$Detail) {
    $script:Warns.Add("WARN $Check $(Format-Where $File $Key $Lang) $Detail")
}

# ---- the dump scan ---------------------------------------------------------------------------------------------

if ($ScanDump) {
    if (-not (Test-Path -LiteralPath $ScanDump)) {
        Write-Host "strings-check: cannot read $ScanDump"
        exit 3
    }
    $allowRx = New-Object 'System.Collections.Generic.List[regex]'
    if ($Allow) {
        if (-not (Test-Path -LiteralPath $Allow)) {
            Write-Host "strings-check: cannot read $Allow"
            exit 3
        }
        foreach ($line in ((Read-Utf8 $Allow) -split "`r?`n")) {
            $l = $line.Trim()
            if (-not $l -or $l.StartsWith('#')) { continue }
            $allowRx.Add((New-Object System.Text.RegularExpressions.Regex($l)))
        }
    }
    $dump = New-Object System.Xml.XmlDocument
    try {
        $dump.LoadXml((Read-Utf8 $ScanDump))
    } catch {
        Write-Host "strings-check: $ScanDump is not well-formed XML: $($_.Exception.Message)"
        exit 3
    }
    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    $leaks = New-Object 'System.Collections.Generic.List[string]'
    $total = 0
    foreach ($node in $dump.SelectNodes('//node')) {
        foreach ($attr in @('text', 'content-desc')) {
            $v = $node.GetAttribute($attr)
            if (-not $v) { continue }
            if (-not $seen.Add($v)) { continue }
            $total++
            if ([regex]::Matches($v, '\p{L}').Count -lt 3) { continue }
            if ($v.Contains('[') -or $v.Contains(']')) { continue }
            $ok = $false
            foreach ($rx in $allowRx) { if ($rx.IsMatch($v)) { $ok = $true; break } }
            if (-not $ok) { $leaks.Add($v) }
        }
    }
    foreach ($leak in $leaks) { Write-Host "LEAK $leak" }
    Write-Host "strings-check: scan texts=$total leaks=$($leaks.Count)"
    if ($leaks.Count -gt 0) { exit 4 }
    exit 0
}

# ---- reading ---------------------------------------------------------------------------------------------------

function Get-EffLen([string]$Raw) {
    $t = [regex]::Replace($Raw, '<[^>]*>', '')
    $t = [regex]::Replace($t, $script:PlaceholderPattern, { param($m) if ($m.Value -eq '%%') { '%' } else { 'XX' } })
    $t = [regex]::Replace($t, '\\u[0-9a-fA-F]{4}|\\.|&#?\w+;', 'X')
    return $t.Length
}

# Sorted placeholder tokens of a text, "%1$d" or "%d"; "%%" is not a placeholder.
function Get-Tokens([string]$Text) {
    $t = $Text.Replace('%%', '')
    $list = New-Object 'System.Collections.Generic.List[string]'
    foreach ($m in [regex]::Matches($t, '%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z])')) {
        if ($m.Groups[1].Success) { $list.Add('%' + $m.Groups[1].Value + '$' + $m.Groups[2].Value) }
        else { $list.Add('%' + $m.Groups[2].Value) }
    }
    $arr = $list.ToArray()
    [Array]::Sort($arr, [StringComparer]::Ordinal)
    return , $arr
}

# True when every token of $Sub is in $Super, counted (a multiset).
function Test-SubMultiset([string[]]$Sub, [string[]]$Super) {
    $counts = @{}
    foreach ($s in $Super) { if ($counts.ContainsKey($s)) { $counts[$s]++ } else { $counts[$s] = 1 } }
    foreach ($s in $Sub) {
        if (-not $counts.ContainsKey($s) -or $counts[$s] -le 0) { return $false }
        $counts[$s]--
    }
    return $true
}

function New-Entry([string]$File, [string]$Name, [string]$Type, [bool]$Translatable, [string]$Note, [string]$Kind) {
    return [pscustomobject]@{
        File = $File; Key = $Name; Type = $Type; Translatable = $Translatable
        Note = $Note; Kind = $Kind
        Forms = (New-Object 'System.Collections.Specialized.OrderedDictionary')
    }
}

# Reads one strings file. Returns a Dictionary key -> entry, or $null when the file is not well formed.
function Read-StringsFile([string]$Path, [string]$Lang) {
    $name = [System.IO.Path]::GetFileName($Path)
    $doc = New-Object System.Xml.XmlDocument
    $doc.PreserveWhitespace = $true
    try {
        $doc.LoadXml((Read-Utf8 $Path))
    } catch {
        Add-Err 'xml' $name '' $Lang ('not well-formed: ' + $_.Exception.Message)
        return $null
    }
    $entries = New-Object 'System.Collections.Generic.Dictionary[string,object]'
    $note = $null
    $kind = $null
    foreach ($node in $doc.DocumentElement.ChildNodes) {
        $nt = [string]$node.NodeType
        if ($nt -eq 'Comment') {
            $c = $node.Value
            $note = $null
            $kind = $null
            if ($c -match '^\s*t:\s*(.+?)\s*$') {
                $note = $Matches[1]
                if ($note -match '\|\s*kind:\s*([a-z]+)\s*$') { $kind = $Matches[1] }
            }
            continue
        }
        if ($nt -eq 'Whitespace' -or $nt -eq 'SignificantWhitespace') {
            if (([regex]::Matches($node.Value, "`n")).Count -ge 2) { $note = $null; $kind = $null }
            continue
        }
        if ($nt -ne 'Element') { continue }
        $ln = $node.LocalName
        if ($ln -ne 'string' -and $ln -ne 'plurals' -and $ln -ne 'string-array') {
            Add-Err 'xml' $name '' $Lang "unexpected element <$($node.Name)>"
            $note = $null; $kind = $null
            continue
        }
        $key = $node.GetAttribute('name')
        if (-not $key) {
            Add-Err 'xml' $name '' $Lang "<$ln> without a name"
            $note = $null; $kind = $null
            continue
        }
        $translatable = ($node.GetAttribute('translatable') -ne 'false')
        $type = $ln
        if ($ln -eq 'string-array') { $type = 'array' }
        $entry = New-Entry $name $key $type $translatable $note $kind
        if ($ln -eq 'string') {
            $entry.Forms['_'] = [pscustomobject]@{ Text = $node.InnerText; Raw = $node.InnerXml }
        } else {
            $i = 0
            foreach ($child in $node.ChildNodes) {
                if ([string]$child.NodeType -ne 'Element') { continue }
                if ($child.LocalName -ne 'item') {
                    Add-Err 'xml' $name $key $Lang "unexpected element <$($child.Name)> in <$ln>"
                    continue
                }
                $q = if ($ln -eq 'plurals') { $child.GetAttribute('quantity') } else { [string]$i }
                $i++
                if (-not $q) {
                    Add-Err 'xml' $name $key $Lang '<item> without a quantity'
                    continue
                }
                if ($entry.Forms.Contains($q)) {
                    Add-Err 'duplicate' $name $key $Lang "quantity $q twice"
                    continue
                }
                $entry.Forms[$q] = [pscustomobject]@{ Text = $child.InnerText; Raw = $child.InnerXml }
            }
            if ($ln -eq 'plurals' -and -not $entry.Forms.Contains('other')) {
                Add-Err 'xml' $name $key $Lang 'plurals without an other form'
            }
        }
        if ($entries.ContainsKey($key)) {
            Add-Err 'duplicate' $name $key $Lang 'defined twice in this file'
        } else {
            $entries[$key] = $entry
        }
        $note = $null
        $kind = $null
    }
    return $entries
}

# A language: the folder's file name -> entries, every key checked for blanks and duplicates across files.
function Read-Language([string]$Dir, [string]$Lang) {
    $files = New-Object 'System.Collections.Generic.Dictionary[string,object]'
    $owner = @{}
    $byName = @{}
    foreach ($df in @(Get-ChildItem -LiteralPath $Dir -File -Filter 'strings*.xml')) { $byName[$df.Name] = $df }
    foreach ($f in @((Sort-Ordinal @($byName.Keys)) | ForEach-Object { $byName[$_] })) {
        $entries = Read-StringsFile $f.FullName $Lang
        if ($null -eq $entries) { continue }
        $files[$f.Name] = $entries
        foreach ($key in $entries.Keys) {
            $e = $entries[$key]
            if ($owner.ContainsKey($key)) {
                Add-Err 'duplicate' $f.Name $key $Lang ("also defined in " + $owner[$key])
            } else {
                $owner[$key] = $f.Name
            }
            if ($e.Translatable) {
                foreach ($q in $e.Forms.Keys) {
                    if ([string]::IsNullOrWhiteSpace($e.Forms[$q].Text)) {
                        Add-Err 'blank' $f.Name $key $Lang 'empty text'
                    }
                }
            }
        }
    }
    return $files
}

function Read-Kinds {
    $rules = New-Object 'System.Collections.Generic.List[object]'
    $allow = @{}
    if (Test-Path -LiteralPath $script:KindsPath) {
        $section = ''
        foreach ($line in ((Read-Utf8 $script:KindsPath) -split "`r?`n")) {
            $l = $line.Trim()
            if (-not $l -or $l.StartsWith('#')) { continue }
            if ($l -match '^\[(.+)\]$') { $section = $Matches[1]; continue }
            if ($section -eq 'kinds') {
                $parts = $l -split '\s+', 2
                if ($parts.Count -eq 2) { $rules.Add([pscustomobject]@{ Kind = $parts[0]; Glob = $parts[1].Trim() }) }
            } elseif ($section -eq 'plural-allow') {
                $allow[$l] = $true
            }
        }
    }
    return @{ Rules = $rules; PluralAllow = $allow }
}

function Get-KindOf($Entry, $Rules) {
    if ($Entry.Kind) { return $Entry.Kind }
    foreach ($r in $Rules) { if ($Entry.Key -like $r.Glob) { return $r.Kind } }
    return 'body'
}

# ---- find the languages ----------------------------------------------------------------------------------------

if (-not (Test-Path -LiteralPath $ResDir)) {
    Write-Host "strings-check: cannot find $ResDir"
    exit 3
}
$mainDir = Split-Path -Parent $ResDir
$srcDir = Split-Path -Parent $mainDir
$langDirs = @{}
foreach ($d in (Get-ChildItem -LiteralPath $ResDir -Directory)) {
    if ($d.Name -ceq 'values') {
        $langDirs['en'] = $d.FullName
    } elseif ($d.Name -ceq ('values-en-' + 'rXZ')) {
        Add-Err 'stretched' $d.Name '' '' 'the stretched language must never be part of src/main (release builds would ship it)'
    } elseif ($d.Name -cmatch '^values-([a-z]{2,3}(-r[A-Z]{2})?|b\+.+)$') {
        $langDirs[$Matches[1]] = $d.FullName
    }
}
if (-not $langDirs.ContainsKey('en')) {
    Write-Host "strings-check: no values folder in $ResDir"
    exit 3
}
$others = @(Sort-Ordinal @($langDirs.Keys | Where-Object { $_ -ne 'en' }))
$langOrder = @('en') + $others

$data = @{}
foreach ($lang in $langOrder) { $data[$lang] = Read-Language $langDirs[$lang] $lang }
$en = $data['en']

$kinds = Read-Kinds
$kindRules = $kinds.Rules

$englishKeys = New-Object 'System.Collections.Generic.HashSet[string]'
$keyCount = 0
foreach ($fn in $en.Keys) {
    foreach ($key in $en[$fn].Keys) {
        [void]$englishKeys.Add($key)
        $keyCount++
    }
}

# ---- parity and placeholders -----------------------------------------------------------------------------------

foreach ($fn in $en.Keys) {
    foreach ($key in $en[$fn].Keys) {
        $e = $en[$fn][$key]
        if (-not $e.Translatable) { continue }
        # English itself: two unnumbered placeholders cannot be reordered by a translator.
        foreach ($q in $e.Forms.Keys) {
            $tok = Get-Tokens $e.Forms[$q].Text
            $plain = @($tok | Where-Object { $_ -notmatch '\$' })
            if ($plain.Count -gt 1) { Add-Err 'placeholder' $fn $key 'en' "more than one placeholder without a number ($($plain -join ' '))" }
        }
        if ($e.Type -eq 'plurals' -and $e.Forms.Contains('other')) {
            $otherTok = Get-Tokens $e.Forms['other'].Text
            foreach ($q in $e.Forms.Keys) {
                if ($q -eq 'other') { continue }
                if (-not (Test-SubMultiset (Get-Tokens $e.Forms[$q].Text) $otherTok)) {
                    Add-Err 'placeholder' $fn $key 'en' "form $q has a placeholder that other does not have"
                }
            }
        }
    }
}

foreach ($lang in $others) {
    $dl = $data[$lang]
    $names = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($n in $en.Keys) { [void]$names.Add($n) }
    foreach ($n in $dl.Keys) { [void]$names.Add($n) }
    foreach ($fn in (Sort-Ordinal @($names))) {
        $hasEn = $en.ContainsKey($fn)
        $hasL = $dl.ContainsKey($fn)
        if ($hasEn -and -not $hasL) { Add-Err 'parity' $fn '' $lang 'file is missing in this language'; continue }
        if ($hasL -and -not $hasEn) { Add-Err 'parity' $fn '' $lang 'file exists only in this language'; continue }
        $ef = $en[$fn]
        $lf = $dl[$fn]
        foreach ($key in $ef.Keys) {
            $ee = $ef[$key]
            if (-not $ee.Translatable) { continue }
            if (-not $lf.ContainsKey($key)) { Add-Err 'parity' $fn $key $lang 'missing'; continue }
            $le = $lf[$key]
            if ($le.Type -ne $ee.Type) { Add-Err 'parity' $fn $key $lang "is a $($le.Type) here and a $($ee.Type) in English"; continue }
            if ($ee.Type -eq 'string') {
                $et = Get-Tokens $ee.Forms['_'].Text
                $lt = Get-Tokens $le.Forms['_'].Text
                if (($et -join ',') -cne ($lt -join ',')) {
                    Add-Err 'placeholder' $fn $key $lang "placeholders differ: English [$($et -join ' ')], here [$($lt -join ' ')]"
                }
            } elseif ($ee.Type -eq 'plurals') {
                if ($ee.Forms.Contains('other') -and $le.Forms.Contains('other')) {
                    $et = Get-Tokens $ee.Forms['other'].Text
                    $lt = Get-Tokens $le.Forms['other'].Text
                    if (($et -join ',') -cne ($lt -join ',')) {
                        Add-Err 'placeholder' $fn $key $lang "placeholders of other differ: English [$($et -join ' ')], here [$($lt -join ' ')]"
                    }
                    foreach ($q in $le.Forms.Keys) {
                        if ($q -eq 'other') { continue }
                        if (-not (Test-SubMultiset (Get-Tokens $le.Forms[$q].Text) $et)) {
                            Add-Err 'placeholder' $fn $key $lang "form $q has a placeholder that English other does not have"
                        }
                    }
                }
            } else {
                if ($ee.Forms.Count -ne $le.Forms.Count) { Add-Err 'parity' $fn $key $lang "array has $($le.Forms.Count) items, English $($ee.Forms.Count)" }
            }
        }
        foreach ($key in $lf.Keys) {
            $le = $lf[$key]
            if (-not $le.Translatable) { continue }
            if (-not $ef.ContainsKey($key)) { Add-Err 'parity' $fn $key $lang 'is not in the English file (orphan)' }
        }
    }
}

# ---- the stretched language and the protected list -------------------------------------------------------------

$stretchDir = Join-Path (Join-Path $srcDir 'debug') ('res/values-en-' + 'rXZ')
$stretchNames = 0
if (Test-Path -LiteralPath $stretchDir) {
    $sd = Read-Language $stretchDir 'en-XZ'
    foreach ($fn in $en.Keys) {
        if (-not $sd.ContainsKey($fn)) { Add-Err 'stretched' $fn '' 'en-XZ' 'file is missing (run tools\make-test-language.ps1)'; continue }
        foreach ($key in $en[$fn].Keys) {
            $ee = $en[$fn][$key]
            if (-not $ee.Translatable) { continue }
            if (-not $sd[$fn].ContainsKey($key)) { Add-Err 'stretched' $fn $key 'en-XZ' 'missing (run tools\make-test-language.ps1)'; continue }
            $se = $sd[$fn][$key]
            foreach ($q in $ee.Forms.Keys) {
                if (-not $se.Forms.Contains($q)) { Add-Err 'stretched' $fn $key 'en-XZ' "form $q missing"; continue }
                $et = Get-Tokens $ee.Forms[$q].Text
                $st = Get-Tokens $se.Forms[$q].Text
                if (($et -join ',') -cne ($st -join ',')) { Add-Err 'stretched' $fn $key 'en-XZ' 'placeholders differ' }
                $want = [int][math]::Floor($script:StretchFactor * (Get-EffLen $ee.Forms[$q].Raw) + 1e-9)
                if ((Get-EffLen $se.Forms[$q].Raw) -lt $want) { Add-Err 'stretched' $fn $key 'en-XZ' "shorter than 1.4 times the English text ($want)" }
            }
            $stretchNames++
        }
    }
    foreach ($fn in $sd.Keys) {
        if (-not $en.ContainsKey($fn)) { Add-Err 'stretched' $fn '' 'en-XZ' 'file is not in the English folder (stale)'; continue }
        foreach ($key in $sd[$fn].Keys) {
            if (-not $en[$fn].ContainsKey($key)) { Add-Err 'stretched' $fn $key 'en-XZ' 'is not in the English file (stale)' }
        }
    }
}

$protectedPath = Join-Path (Join-Path $mainDir 'assets') 'text/protected-keys.txt'
if (Test-Path -LiteralPath $protectedPath) {
    foreach ($line in ((Read-Utf8 $protectedPath) -split "`r?`n")) {
        $l = $line.Trim()
        if (-not $l -or $l.StartsWith('#') -or $l.EndsWith('*')) { continue }
        if (-not $englishKeys.Contains($l)) { Add-Err 'protected' 'protected-keys.txt' $l '' 'names a text that does not exist' }
    }
} else {
    $script:Infos.Add("INFO protected-keys.txt not found at $protectedPath")
}

# ---- notes, kinds, length --------------------------------------------------------------------------------------

$violations = New-Object 'System.Collections.Generic.List[object]'
$tight = 0
foreach ($lang in $langOrder) {
    foreach ($fn in $data[$lang].Keys) {
        foreach ($key in $data[$lang][$fn].Keys) {
            $e = $data[$lang][$fn][$key]
            if (-not $e.Translatable -or $e.Type -eq 'array') { continue }
            $kind = Get-KindOf $e $kindRules
            if ($kind -ne 'body' -and -not $script:Rooms.ContainsKey($kind)) {
                if ($lang -eq 'en') { Add-Err 'kind' $fn $key '' "unknown kind '$kind'" }
                continue
            }
            if ($kind -eq 'body') { continue }
            $room = [int]$script:Rooms[$kind]
            $limit = $room
            if ($lang -ne 'en') { $limit = [int][math]::Floor($room * $Factor + 1e-9) }
            $worst = 0
            foreach ($q in $e.Forms.Keys) {
                $len = Get-EffLen $e.Forms[$q].Raw
                if ($len -gt $worst) { $worst = $len }
            }
            if ($worst -gt $limit) {
                $violations.Add([pscustomobject]@{
                    Id = "${fn}:${key}:${lang}"; File = $fn; Key = $key; Lang = $lang
                    Detail = "$worst characters, room for $limit ($kind)"
                })
            } elseif ($lang -ne 'en' -and $worst -gt $room) {
                $tight++
            }
        }
    }
}

foreach ($fn in $en.Keys) {
    foreach ($key in $en[$fn].Keys) {
        $e = $en[$fn][$key]
        if (-not $e.Translatable) { continue }
        if (-not $e.Note) {
            if ($fn -eq 'strings_engine.xml') { Add-Err 'note' $fn $key '' 'no translator note (an XML comment "t: ..." directly above)' }
            else { Add-Warn 'note' $fn $key '' 'no translator note' }
        }
    }
}

# ---- count nouns -----------------------------------------------------------------------------------------------

foreach ($fn in $en.Keys) {
    foreach ($key in $en[$fn].Keys) {
        $e = $en[$fn][$key]
        if (-not $e.Translatable -or $e.Type -ne 'string') { continue }
        if ($kinds.PluralAllow.ContainsKey($key)) { continue }
        foreach ($m in [regex]::Matches($e.Forms['_'].Text, '%(?:\d+\$)?d\s+(\p{L}{3,})')) {
            $word = $m.Groups[1].Value
            $isUnit = $false
            foreach ($u in $script:Units) { if ($u -ieq $word) { $isUnit = $true } }
            if (-not $isUnit) {
                Add-Warn 'plural' $fn $key '' "a count noun after a bare number ('$word'): make it a plurals entry"
                break
            }
        }
    }
}

# ---- unused keys -----------------------------------------------------------------------------------------------

$used = New-Object 'System.Collections.Generic.HashSet[string]'
$manifestKeys = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($f in (Get-ChildItem -LiteralPath $mainDir -Recurse -File)) {
    $ext = $f.Extension
    if ($ext -ne '.kt' -and $ext -ne '.xml') { continue }
    if ($f.Directory.Name -like 'values*') { continue }
    $text = Read-Utf8 $f.FullName
    foreach ($m in [regex]::Matches($text, 'R\.(?:string|plurals)\.([A-Za-z0-9_]+)')) { [void]$used.Add($m.Groups[1].Value) }
    foreach ($m in [regex]::Matches($text, '@(?:string|plurals)/([A-Za-z0-9_]+)')) {
        [void]$used.Add($m.Groups[1].Value)
        if ($ext -eq '.xml') { [void]$manifestKeys.Add($m.Groups[1].Value) }
    }
}
foreach ($fn in $en.Keys) {
    foreach ($key in $en[$fn].Keys) {
        if ($used.Contains($key)) { continue }
        if ($FailOnUnused) { Add-Err 'unused' $fn $key '' 'nothing refers to this key' }
        else { Add-Warn 'unused' $fn $key '' 'nothing refers to this key' }
    }
}
if ($manifestKeys.Count -gt 0) {
    $script:Infos.Add('INFO the manifest and res/xml refer to: ' + ((Sort-Ordinal @($manifestKeys)) -join ', ') + ' (only built-in languages can supply them)')
}

# ---- the baseline ----------------------------------------------------------------------------------------------

$known = New-Object 'System.Collections.Generic.HashSet[string]'
$baselineWarnings = $null
if ($WriteBaseline) {
    $ids = @(Sort-OrdinalUnique @($violations | ForEach-Object { $_.Id }))
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("{`n  `"warnings`": $($script:Warns.Count),`n  `"lengthKnown`": [")
    for ($i = 0; $i -lt $ids.Count; $i++) {
        if ($i -gt 0) { [void]$sb.Append(',') }
        [void]$sb.Append("`n    `"$($ids[$i])`"")
    }
    if ($ids.Count -gt 0) { [void]$sb.Append("`n  ") }
    [void]$sb.Append("]`n}`n")
    [System.IO.File]::WriteAllText($script:BaselinePath, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
    foreach ($id in $ids) { [void]$known.Add($id) }
    $baselineWarnings = $script:Warns.Count
    $script:Infos.Add("INFO baseline written: warnings=$($script:Warns.Count) lengthKnown=$($ids.Count)")
} elseif (Test-Path -LiteralPath $script:BaselinePath) {
    try {
        $b = (Read-Utf8 $script:BaselinePath) | ConvertFrom-Json
        $baselineWarnings = [int]$b.warnings
        foreach ($id in @($b.lengthKnown)) { if ($id) { [void]$known.Add([string]$id) } }
    } catch {
        Write-Host "strings-check: cannot read $($script:BaselinePath): $($_.Exception.Message)"
        exit 3
    }
}
$knownCount = 0
foreach ($v in $violations) {
    if ($known.Contains($v.Id)) { $knownCount++; continue }
    Add-Err 'length' $v.File $v.Key $v.Lang $v.Detail
}

# ---- report ----------------------------------------------------------------------------------------------------

foreach ($line in $script:Errors) { Write-Host $line }
if (-not $Quiet) {
    foreach ($line in $script:Warns) { Write-Host $line }
    foreach ($line in $script:Infos) { Write-Host $line }
    if ($tight -gt 0) { Write-Host "INFO $tight translated texts are longer than their room but fit the stretch factor" }
    if ($knownCount -gt 0) { Write-Host "INFO $knownCount known length violations are tolerated by the baseline" }
}

$baselineText = '-'
if ($null -ne $baselineWarnings) { $baselineText = [string]$baselineWarnings }
$ratchetWorse = ($null -ne $baselineWarnings) -and ($script:Warns.Count -gt $baselineWarnings)
Write-Host ("strings-check: languages=" + ($langOrder -join ',') + " keys=$keyCount errors=$($script:Errors.Count) warnings=$($script:Warns.Count) (baseline $baselineText)")

if ($script:Errors.Count -gt 0) { exit 1 }
if ($ratchetWorse) {
    Write-Host "strings-check: more warnings than the baseline ($($script:Warns.Count) > $baselineWarnings); fix them or ask the lead to write a new baseline"
    exit 2
}
exit 0
