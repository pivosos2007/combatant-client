param(
    [Parameter(Mandatory = $true)][string]$SourceDirectory,
    [Parameter(Mandatory = $true)][string]$AddonAssetsDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetDirectory = (Resolve-Path -LiteralPath $AddonAssetsDirectory).Path
$catalog = (Get-Content -Raw -LiteralPath (Join-Path $assetDirectory 'combatant/player_models/armor.json') | ConvertFrom-Json).entries
foreach ($entry in $catalog) {
    if (-not $entry.icon) { continue }
    $destination = [IO.Path]::GetFullPath((Join-Path $assetDirectory $entry.icon))
    if (-not $destination.StartsWith($assetDirectory + [IO.Path]::DirectorySeparatorChar)) { throw 'Icon path escapes addon assets' }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
    $source = Join-Path $SourceDirectory ([IO.Path]::GetFileName($entry.icon))
    $image = [Drawing.Image]::FromFile($source)
    try {
        $ratio = [Math]::Min(1.0, 256.0 / [Math]::Max($image.Width, $image.Height))
        $width = [Math]::Max(1, [int][Math]::Round($image.Width * $ratio))
        $height = [Math]::Max(1, [int][Math]::Round($image.Height * $ratio))
        $bitmap = New-Object Drawing.Bitmap($width, $height, ([Drawing.Imaging.PixelFormat]::Format32bppArgb))
        try {
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.Clear([Drawing.Color]::Transparent)
                $graphics.DrawImage($image, 0, 0, $width, $height)
            } finally { $graphics.Dispose() }
            $bitmap.Save($destination, [Drawing.Imaging.ImageFormat]::Png)
        } finally { $bitmap.Dispose() }
    } finally { $image.Dispose() }
}
Write-Output ('Prepared {0} production icons, at most 256 x 256.' -f $catalog.Count)
