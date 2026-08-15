param(
    [string]$OutDir = (Join-Path $PSScriptRoot '..\packaging\icons')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$size = 256
$outDir = [System.IO.Path]::GetFullPath($OutDir)
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$top = [System.Drawing.Color]::FromArgb(255, 27, 67, 50)
$bottom = [System.Drawing.Color]::FromArgb(255, 45, 106, 79)
$rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $rect, $top, $bottom, 75.0)
$radius = 44
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$d = 2 * $radius
$path.AddArc(0, 0, $d, $d, 180, 90)
$path.AddArc($size - $d, 0, $d, $d, 270, 90)
$path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
$path.AddArc(0, $size - $d, $d, $d, 90, 90)
$path.CloseFigure()
$g.FillPath($brush, $path)

$accent = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 149, 213, 178))
$g.FillEllipse($accent, 40, 40, 176, 176)

$white = [System.Drawing.Color]::White
$font = New-Object System.Drawing.Font('Segoe UI', 150, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$sf = New-Object System.Drawing.StringFormat
$sf.Alignment = [System.Drawing.StringAlignment]::Center
$sf.LineAlignment = [System.Drawing.StringAlignment]::Center
$textRect = New-Object System.Drawing.RectangleF(0, -8, $size, $size)
$g.DrawString('B', $font, (New-Object System.Drawing.SolidBrush($white)), $textRect, $sf)

$pngPath = Join-Path $outDir 'boquila.png'
$bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)

$pngBytes = [System.IO.File]::ReadAllBytes($pngPath)
$icoPath = Join-Path $outDir 'boquila.ico'
$fs = [System.IO.File]::Create($icoPath)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([UInt16]0)
$bw.Write([UInt16]1)
$bw.Write([UInt16]1)
$bw.Write([Byte]0)
$bw.Write([Byte]0)
$bw.Write([Byte]0)
$bw.Write([Byte]0)
$bw.Write([UInt16]1)
$bw.Write([UInt16]32)
$bw.Write([UInt32]$pngBytes.Length)
$bw.Write([UInt32]22)
$bw.Write($pngBytes)
$bw.Close()

$g.Dispose()
$bmp.Dispose()
Write-Host "Icons written: $pngPath, $icoPath"