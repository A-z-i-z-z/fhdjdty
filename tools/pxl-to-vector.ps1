<#
.SYNOPSIS
  Converts one frame of a .pxl sprite into an Android <vector> drawable (one <path> per colour,
  runs of equal pixels merged into rectangles, so the file stays small).

.DESCRIPTION
  The vector has a 108 x 108 viewport by default (adaptive icon layers). One sprite pixel becomes
  -Pixel viewport units (default 1.2); the art is centred unless -OffsetX/-OffsetY are given.
  For a launcher foreground keep the art inside the 66 dp safe zone: 44 px x 1.2 = 52.8 units.

.PARAMETER File      the .pxl file
.PARAMETER Sprite    sprite name (required)
.PARAMETER Out       output .xml path (required)
.PARAMETER Frame     frame index (default 0)
.PARAMETER Pixel     viewport units per sprite pixel (default 1.2)
.PARAMETER Viewport  viewport width and height (default 108)
.PARAMETER SizeDp    drawable width/height in dp (default = Viewport)
.PARAMETER OffsetX   left offset in viewport units (default: centred)
.PARAMETER OffsetY   top offset in viewport units (default: centred)
.PARAMETER Mono      every opaque pixel becomes opaque white (themed / notification icons)
.PARAMETER Tint      every opaque pixel becomes this colour (#RRGGBB)

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\pxl-to-vector.ps1 -File app\src\main\assets\pixel\appicon.pxl -Sprite icon_fg -Out app\src\main\res\drawable\ic_launcher_foreground.xml
#>
param(
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$Sprite,
    [Parameter(Mandatory = $true)][string]$Out,
    [int]$Frame = 0,
    [double]$Pixel = 1.2,
    [double]$Viewport = 108,
    [double]$SizeDp = 0,
    [double]$OffsetX = [double]::NaN,
    [double]$OffsetY = [double]::NaN,
    [switch]$Mono,
    [string]$Tint = ''
)

$ErrorActionPreference = 'Stop'
$inv = [Globalization.CultureInfo]::InvariantCulture
if ($SizeDp -le 0) { $SizeDp = $Viewport }

function Fmt([double]$v) {
    $s = [Math]::Round($v, 3).ToString('0.###', $inv)
    if ($s -eq '-0') { $s = '0' }
    return $s
}

# ---- parse (same grammar as pixel-preview.ps1 / SpriteBank.kt) ----
$name = [IO.Path]::GetFileName($File)
$lines = [IO.File]::ReadAllLines((Resolve-Path $File).Path, [Text.Encoding]::UTF8)
$palette = @{}; $found = $null; $cur = $null; $i = 0
while ($i -lt $lines.Length) {
    $ln = $lines[$i]; $i++
    if ($ln.Trim().Length -eq 0 -or $ln.StartsWith('#')) { continue }
    $tok = @($ln.Trim() -split '\s+')
    if ($tok[0] -eq 'palette' -and $tok.Count -eq 1) { $palette = @{}; continue }
    if ($tok[0] -eq 'sprite' -and $tok.Count -eq 4) {
        $cur = [pscustomobject]@{ Name = $tok[1]; W = [int]$tok[2]; H = [int]$tok[3]; Frames = (New-Object System.Collections.Generic.List[object]); Palette = $palette.Clone() }
        if ($tok[1] -eq $Sprite) { $found = $cur }
        continue
    }
    if ($tok[0] -eq 'frame' -and $tok.Count -le 2) {
        $rows = New-Object 'string[]' $cur.H
        for ($r = 0; $r -lt $cur.H; $r++) {
            $row = $lines[$i]; $i++
            if ($row.Length -ne $cur.W) { throw "${name}:${i}: row has $($row.Length) chars, expected $($cur.W)" }
            $rows[$r] = $row
        }
        $cur.Frames.Add($rows); continue
    }
    if ($tok.Count -eq 2 -and $tok[0].Length -eq 1) { $palette[$tok[0][0]] = $tok[1]; continue }
    throw "${name}:${i}: cannot parse line '$ln'"
}
if ($null -eq $found) { throw "no sprite named '$Sprite' in $File" }
if ($Frame -lt 0 -or $Frame -ge $found.Frames.Count) { throw "sprite '$Sprite' has $($found.Frames.Count) frame(s), asked for $Frame" }
$rows = $found.Frames[$Frame]
$W = $found.W; $H = $found.H

if ([double]::IsNaN($OffsetX)) { $OffsetX = ($Viewport - $W * $Pixel) / 2 }
if ([double]::IsNaN($OffsetY)) { $OffsetY = ($Viewport - $H * $Pixel) / 2 }

function ArgbOf([string]$text) {
    if ($Mono) { return '#FFFFFFFF' }
    if ($text -eq 'none') { return $null }
    if ($text -match '^#([0-9A-Fa-f]{6})$') { $v = $Matches[1].ToUpper() }
    elseif ($text -match '^#([0-9A-Fa-f]{8})$') { $v = $Matches[1].ToUpper(); return '#' + $v }
    else { throw "bad colour '$text'" }
    if ($Tint -match '^#([0-9A-Fa-f]{6})$') { $v = $Matches[1].ToUpper() }
    return '#FF' + $v
}

# ---- merge: horizontal runs, then identical runs in consecutive rows become one rectangle ----
$paths = [ordered]@{}      # colour -> StringBuilder
$open = @{}                # "x,w,ch" -> start row
function Emit([string]$key, [int]$y0, [int]$y1) {
    $p = $key.Split(',')
    $x = [int]$p[0]; $w = [int]$p[1]; $ch = [char]$p[2]
    $argb = ArgbOf $found.Palette[$ch]
    if ($null -eq $argb) { return }
    if (-not $paths.Contains($argb)) { $paths[$argb] = New-Object System.Text.StringBuilder }
    $px = $OffsetX + $x * $Pixel; $py = $OffsetY + $y0 * $Pixel
    [void]$paths[$argb].Append('M' + (Fmt $px) + ',' + (Fmt $py) + 'h' + (Fmt ($w * $Pixel)) + 'v' + (Fmt (($y1 - $y0 + 1) * $Pixel)) + 'h' + (Fmt (-$w * $Pixel)) + 'z')
}
for ($y = 0; $y -le $H; $y++) {
    $now = @{}
    if ($y -lt $H) {
        $row = $rows[$y]; $x = 0
        while ($x -lt $W) {
            $ch = $row[$x]
            if ($ch -eq '.') { $x++; continue }
            if (-not $found.Palette.ContainsKey($ch)) { throw "${name}: char '$ch' not in palette (sprite $Sprite, row $y)" }
            $x0 = $x
            while ($x -lt $W -and $row[$x] -eq $ch) { $x++ }
            $now["$x0,$($x - $x0),$ch"] = $true
        }
    }
    foreach ($k in @($open.Keys)) {
        if (-not $now.ContainsKey($k)) { Emit $k $open[$k] ($y - 1); $open.Remove($k) }
    }
    foreach ($k in $now.Keys) { if (-not $open.ContainsKey($k)) { $open[$k] = $y } }
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('<?xml version="1.0" encoding="utf-8"?>')
[void]$sb.AppendLine("<!-- Generated by tools/pxl-to-vector.ps1 from $name sprite '$Sprite' (frame $Frame). Edit the .pxl, not this file. -->")
[void]$sb.AppendLine('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
[void]$sb.AppendLine("    android:width=""$(Fmt $SizeDp)dp"" android:height=""$(Fmt $SizeDp)dp""")
[void]$sb.AppendLine("    android:viewportWidth=""$(Fmt $Viewport)"" android:viewportHeight=""$(Fmt $Viewport)"">")
foreach ($k in $paths.Keys) {
    [void]$sb.AppendLine("    <path android:fillColor=""$k"" android:pathData=""$($paths[$k].ToString())""/>")
}
[void]$sb.AppendLine('</vector>')
$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
[IO.File]::WriteAllText($Out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("{0}: {1} colour(s), {2} bytes" -f $Out, $paths.Count, $sb.Length)
