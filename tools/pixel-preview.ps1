<#
.SYNOPSIS
  Renders .pxl sprite files (see docs/phase1-contract.md) to PNG sheets you can look at.

.DESCRIPTION
  Windows PowerShell 5.1 + System.Drawing, no other dependencies. Every sprite becomes one PNG with all
  its frames side by side on a checkerboard (so transparency is visible), scaled with nearest neighbour.
  The parser is as strict as the app's (app/src/main/java/.../ui/pixel/SpriteBank.kt) and reports
  "file:line: message" for the first problem it finds.

.PARAMETER File    One .pxl file, or a directory (every *.pxl in it). Default: app\src\main\assets\pixel
.PARAMETER Sprite  Only this sprite (default: all sprites in the file)
.PARAMETER Scale   Device pixels per sprite pixel (default 8)
.PARAMETER Out     Output directory (default: <repo>\dist\pixel-preview, git-ignored)
.PARAMETER Ramp    Four comma-separated colours (#RRGGBB) that replace palette chars 1..4 (the tint ramp)
.PARAMETER Tint    One colour (#RRGGBB): every opaque pixel is drawn in it (monochrome icons)
.PARAMETER Bg      Flat background colour (#RRGGBB) instead of the checkerboard
.PARAMETER Sheet   Also write one contact sheet per file (<file>-sheet.png), all sprites in rows

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\pixel-preview.ps1 -File app\src\main\assets\pixel\mascot.pxl -Sprite pet_idle -Scale 10
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\pixel-preview.ps1 -File app\src\main\assets\pixel\avatars.pxl -Sheet -Ramp "#3B2A5A,#5E448C,#8A6AC8,#BFA4F2"
#>
param(
    [string]$File = '',
    [string]$Sprite = '',
    [int]$Scale = 8,
    [string]$Out = '',
    [string]$Ramp = '',
    [string]$Tint = '',
    [string]$Bg = '',
    [switch]$Sheet
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repo = Split-Path -Parent $PSScriptRoot
if (-not $File) { $File = Join-Path $repo 'app\src\main\assets\pixel' }
if (-not $Out) { $Out = Join-Path $repo 'dist\pixel-preview' }
if (-not (Test-Path $Out)) { New-Item -ItemType Directory -Force -Path $Out | Out-Null }

function Convert-Color([string]$text, [string]$where) {
    # returns @(a, r, g, b); a = 0 means transparent
    if ($text -eq 'none') { return @(0, 0, 0, 0) }
    if ($text -match '^#([0-9A-Fa-f]{6})$') {
        $v = $Matches[1]
        return @(255, [Convert]::ToInt32($v.Substring(0, 2), 16), [Convert]::ToInt32($v.Substring(2, 2), 16), [Convert]::ToInt32($v.Substring(4, 2), 16))
    }
    if ($text -match '^#([0-9A-Fa-f]{8})$') {
        $v = $Matches[1]
        return @([Convert]::ToInt32($v.Substring(0, 2), 16), [Convert]::ToInt32($v.Substring(2, 2), 16), [Convert]::ToInt32($v.Substring(4, 2), 16), [Convert]::ToInt32($v.Substring(6, 2), 16))
    }
    throw "$where`: bad colour '$text' (use #RRGGBB, #AARRGGBB or none)"
}

function Read-Pxl([string]$path) {
    $name = [IO.Path]::GetFileName($path)
    $lines = [IO.File]::ReadAllLines($path, [Text.Encoding]::UTF8)
    $palette = @{}          # char -> @(a,r,g,b) ; current block
    $sprites = New-Object System.Collections.Generic.List[object]
    $cur = $null
    $i = 0
    while ($i -lt $lines.Length) {
        $ln = $lines[$i]; $i++
        $where = "${name}:$i"
        if ($ln.Trim().Length -eq 0 -or $ln.StartsWith('#')) { continue }
        $tok = @($ln.Trim() -split '\s+')
        if ($tok[0] -eq 'palette' -and $tok.Count -eq 1) { $palette = @{}; $cur = $null; continue }
        if ($tok[0] -eq 'sprite' -and $tok.Count -eq 4) {
            $w = 0; $h = 0
            if (-not [int]::TryParse($tok[2], [ref]$w) -or -not [int]::TryParse($tok[3], [ref]$h) -or $w -le 0 -or $h -le 0) { throw "$where`: bad sprite size '$($tok[2]) $($tok[3])'" }
            foreach ($s in $sprites) { if ($s.Name -eq $tok[1]) { throw "$where`: duplicate sprite name '$($tok[1])'" } }
            $cur = [pscustomobject]@{ Name = $tok[1]; W = $w; H = $h; Frames = (New-Object System.Collections.Generic.List[object]); Palette = $palette.Clone() }
            $sprites.Add($cur); continue
        }
        if ($tok[0] -eq 'frame' -and $tok.Count -le 2) {
            if ($null -eq $cur) { throw "$where`: 'frame' before any 'sprite'" }
            $ms = 120
            if ($tok.Count -eq 2 -and (-not [int]::TryParse($tok[1], [ref]$ms) -or $ms -le 0)) { throw "$where`: bad frame duration '$($tok[1])'" }
            $rows = New-Object 'string[]' $cur.H
            for ($r = 0; $r -lt $cur.H; $r++) {
                if ($i -ge $lines.Length) { throw "${name}:$($lines.Length): sprite '$($cur.Name)' frame $($cur.Frames.Count) ends after $r of $($cur.H) rows" }
                $row = $lines[$i]; $i++
                if ($row.Length -ne $cur.W) { throw "${name}:${i}: row has $($row.Length) chars, expected $($cur.W) (sprite '$($cur.Name)', frame $($cur.Frames.Count), row $r)" }
                foreach ($ch in $row.ToCharArray()) {
                    if ($ch -ne '.' -and -not $cur.Palette.ContainsKey($ch)) { throw "${name}:${i}: char '$ch' is not in the palette (sprite '$($cur.Name)')" }
                }
                $rows[$r] = $row
            }
            $cur.Frames.Add([pscustomobject]@{ Ms = $ms; Rows = $rows }); continue
        }
        if ($tok.Count -eq 2 -and $tok[0].Length -eq 1) {
            $c = $tok[0][0]
            if ($c -eq '#') { throw "$where`: '#' cannot be a palette char" }
            if ($c -eq '.' -and $tok[1] -ne 'none') { throw "$where`: '.' is always transparent" }
            $palette[$c] = Convert-Color $tok[1] $where
            continue
        }
        throw "$where`: cannot parse line '$ln'"
    }
    foreach ($s in $sprites) { if ($s.Frames.Count -eq 0) { throw "${name}: sprite '$($s.Name)' has no frames" } }
    return $sprites
}

function Get-Ramp([string]$text) {
    if (-not $text) { return $null }
    $parts = @($text -split ',')
    $res = @()
    foreach ($p in $parts) { $res += , (Convert-Color $p.Trim() '-Ramp') }
    while ($res.Count -lt 4) { $res += , $res[$res.Count - 1] }
    return $res
}

function New-Canvas([int]$w, [int]$h, [string]$bg, [int]$cell) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    if ($bg) {
        $c = Convert-Color $bg '-Bg'
        $g.Clear([System.Drawing.Color]::FromArgb($c[0], $c[1], $c[2], $c[3]))
    } else {
        $b1 = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(58, 54, 70))
        $b2 = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(46, 42, 58))
        for ($y = 0; $y * $cell -lt $h; $y++) {
            for ($x = 0; $x * $cell -lt $w; $x++) {
                $g.FillRectangle($(if ((($x + $y) % 2) -eq 0) { $b1 } else { $b2 }), $x * $cell, $y * $cell, $cell, $cell)
            }
        }
        $b1.Dispose(); $b2.Dispose()
    }
    return @{ Bmp = $bmp; G = $g }
}

function Draw-Frame($g, $sprite, [int]$frame, [int]$ox, [int]$oy, [int]$scale, $ramp, $tint) {
    $pal = @{}
    foreach ($k in $sprite.Palette.Keys) { $pal[$k] = $sprite.Palette[$k] }
    if ($ramp) { $n = 0; foreach ($ch in '1', '2', '3', '4') { if ($pal.ContainsKey([char]$ch)) { $pal[[char]$ch] = $ramp[$n] }; $n++ } }
    $brushes = @{}
    foreach ($k in $pal.Keys) {
        $c = $pal[$k]
        if ($c[0] -eq 0) { continue }
        if ($tint) { $c = @($c[0], $tint[1], $tint[2], $tint[3]) }
        $brushes[$k] = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb($c[0], $c[1], $c[2], $c[3]))
    }
    $rows = $sprite.Frames[$frame].Rows
    for ($y = 0; $y -lt $sprite.H; $y++) {
        $row = $rows[$y]
        for ($x = 0; $x -lt $sprite.W; $x++) {
            $ch = $row[$x]
            if ($ch -eq '.') { continue }
            if ($brushes.ContainsKey($ch)) { $g.FillRectangle($brushes[$ch], $ox + $x * $scale, $oy + $y * $scale, $scale, $scale) }
        }
    }
    foreach ($b in $brushes.Values) { $b.Dispose() }
}

$rampColors = Get-Ramp $Ramp
$tintColor = $null
if ($Tint) { $tintColor = Convert-Color $Tint '-Tint' }
$gap = 2

$files = @()
if (Test-Path $File -PathType Container) { $files = @(Get-ChildItem $File -Filter *.pxl | Sort-Object Name | ForEach-Object { $_.FullName }) }
else { $files = @((Resolve-Path $File).Path) }

$written = New-Object System.Collections.Generic.List[string]
foreach ($path in $files) {
    $stem = [IO.Path]::GetFileNameWithoutExtension($path)
    $sprites = Read-Pxl $path
    $picked = @($sprites | Where-Object { (-not $Sprite) -or $_.Name -eq $Sprite })
    if ($Sprite -and $picked.Count -eq 0) { if ($files.Count -eq 1) { throw "no sprite named '$Sprite' in $path" } else { continue } }
    foreach ($sp in $picked) {
        $n = $sp.Frames.Count
        $w = ($sp.W * $n + $gap * ($n + 1)) * $Scale
        $h = ($sp.H + 2 * $gap) * $Scale
        $cv = New-Canvas $w $h $Bg $Scale
        for ($f = 0; $f -lt $n; $f++) {
            Draw-Frame $cv.G $sp $f (($gap + $f * ($sp.W + $gap)) * $Scale) ($gap * $Scale) $Scale $rampColors $tintColor
        }
        $png = Join-Path $Out "$stem-$($sp.Name).png"
        $cv.Bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
        $cv.G.Dispose(); $cv.Bmp.Dispose()
        $written.Add($png)
    }
    if ($Sheet -and $picked.Count -gt 0) {
        $maxW = 0; $total = $gap * $Scale
        foreach ($sp in $picked) {
            $maxW = [Math]::Max($maxW, ($sp.W * $sp.Frames.Count + $gap * ($sp.Frames.Count + 1)) * $Scale)
            $total += ($sp.H + $gap) * $Scale
        }
        $cv = New-Canvas $maxW $total $Bg $Scale
        $y = $gap * $Scale
        foreach ($sp in $picked) {
            for ($f = 0; $f -lt $sp.Frames.Count; $f++) {
                Draw-Frame $cv.G $sp $f (($gap + $f * ($sp.W + $gap)) * $Scale) $y $Scale $rampColors $tintColor
            }
            $y += ($sp.H + $gap) * $Scale
        }
        $png = Join-Path $Out "$stem-sheet.png"
        $cv.Bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
        $cv.G.Dispose(); $cv.Bmp.Dispose()
        $written.Add($png)
    }
}
$written | ForEach-Object { Write-Output $_ }
