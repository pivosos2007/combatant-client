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
    @{ File = "icons.ttf"; Chars = "[0x20,0x7E]" },
    @{ File = "iconsnur.ttf"; Chars = "[0x20,0x7E]" },
    @{ File = "mediaplayer.ttf"; Chars = "[0xEA00,0xEA08]" },
    @{ File = "weather_icons.ttf"; Chars = "[0xEA00,0xEA1F]" },
    @{
        File = "vanilla_symbols.ttf"
        Chars = "[0x20,0x7E], [0xA0,0x17F], [0x370,0x3FF], [0x400,0x52F], " +
                "[0x2000,0x206F], [0x20A0,0x20CF], [0x2100,0x214F], [0x2190,0x21FF], " +
                "[0x2200,0x22FF], [0x2300,0x23FF], [0x2500,0x257F], [0x2580,0x259F], " +
                "[0x25A0,0x25FF], [0x2600,0x26FF], [0x2700,0x27BF], [0x2900,0x29FF], " +
                "[0x2B00,0x2BFF], [0x1F300,0x1F3FF], [0x1F500,0x1F6FF], [0x1FA00,0x1FAFF]"
    }
)

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

foreach ($entry in $fonts) {
    $fontFile = $entry.File
    $font = Join-Path $FontRoot $fontFile
    if (-not (Test-Path -LiteralPath $font -PathType Leaf)) {
        throw "Font source not found: $font"
    }

    $base = [IO.Path]::GetFileNameWithoutExtension($fontFile)
    $png = Join-Path $OutputRoot "$base.png"
    $json = Join-Path $OutputRoot "$base.json"

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $generator `
        -font $font `
        -chars $entry.Chars `
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

    $generatorExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction

    if ($generatorExitCode -ne 0) {
        throw "MSDF generation failed for $fontFile (exit $generatorExitCode)"
    }

    $metadata = Get-Content -LiteralPath $json -Raw | ConvertFrom-Json
    $unicodeGlyphs = @($metadata.glyphs | Where-Object { $null -ne $_.unicode })
    if ($unicodeGlyphs.Count -eq 0) {
        throw "MSDF generation produced no Unicode-mapped glyphs for $fontFile"
    }
}
