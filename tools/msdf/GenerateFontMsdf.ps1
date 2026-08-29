param(
    [string]$FontRoot = "src/main/resources/assets/combatant/font",
    [string]$OutputRoot = "src/main/resources/assets/combatant/font/msdf"
)

$ErrorActionPreference = "Stop"

$generator = Join-Path $PSScriptRoot "msdf-atlas-gen.exe"
if (-not (Test-Path -LiteralPath $generator -PathType Leaf)) {
    throw "MSDF atlas generator not found: $generator"
}

$fonts = @(
    "icons.ttf",
    "iconsnur.ttf",
    "mediaplayer.ttf",
    "vanilla_symbols.ttf",
    "weather_icons.ttf"
)

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

foreach ($fontFile in $fonts) {
    $font = Join-Path $FontRoot $fontFile
    if (-not (Test-Path -LiteralPath $font -PathType Leaf)) {
        throw "Font source not found: $font"
    }

    $base = [IO.Path]::GetFileNameWithoutExtension($fontFile)
    $png = Join-Path $OutputRoot "$base.png"
    $json = Join-Path $OutputRoot "$base.json"

    & $generator `
        -font $font `
        -allglyphs `
        -type msdf `
        -format png `
        -size 48 `
        -pxrange 6 `
        -potr `
        -yorigin top `
        -nokerning `
        -threads 0 `
        -imageout $png `
        -json $json

    if ($LASTEXITCODE -ne 0) {
        throw "MSDF generation failed for $fontFile (exit $LASTEXITCODE)"
    }
}
