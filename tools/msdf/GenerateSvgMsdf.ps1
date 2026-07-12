param(
    [string]$InputRoot = "src/main/resources/assets/combatant/svg",
    [string]$OutRoot = "build/generated/resources/svgMsdf/assets/combatant/svg/msdf",
    [int]$Dimension = 64,
    [int]$PxRange = 4,
    [string]$Type = "mtsdf",
    [string]$PreprocessorClasspath = "build/classes/java/main",
    [string]$JavaExe = "java",
    [switch]$Strict
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path "${PSScriptRoot}\..\..").Path

function Resolve-RepoPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

$inputRootPath = Resolve-RepoPath $InputRoot
$outRootPath = Resolve-RepoPath $OutRoot
$toolDir = Join-Path $repoRoot "tools/msdf"
$msdfgenPath = Join-Path $toolDir "msdfgen.exe"
$workRoot = Join-Path $repoRoot "build/tmp/svg-msdf"

function Get-Msdfgen {
    if (Test-Path $msdfgenPath) { return $msdfgenPath }

    $api = "https://api.github.com/repos/Chlumsky/msdfgen/releases/latest"
    $release = Invoke-RestMethod -Uri $api -Headers @{ "User-Agent" = "CombatantSvgMsdfGen" }
    $asset = $release.assets |
            Where-Object { $_.name -match "win64" -and $_.name -match "\.zip$" -and $_.name -notmatch "openmp" } |
            Select-Object -First 1
    if (-not $asset) {
        $asset = $release.assets |
                Where-Object { $_.name -match "win" -and $_.name -match "\.zip$" -and $_.name -notmatch "openmp" } |
                Select-Object -First 1
    }
    if (-not $asset) { throw "No Windows msdfgen zip found in latest release." }

    New-Item -ItemType Directory -Force -Path $toolDir | Out-Null
    $archivePath = Join-Path $toolDir $asset.name
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archivePath -Headers @{ "User-Agent" = "CombatantSvgMsdfGen" }

    $extractDir = Join-Path $toolDir ([System.IO.Path]::GetFileNameWithoutExtension($asset.name))
    Expand-Archive -Path $archivePath -DestinationPath $extractDir -Force
    $found = Get-ChildItem -Path $extractDir -Filter "msdfgen.exe" -Recurse | Select-Object -First 1
    if (-not $found) { throw "msdfgen.exe not found after extraction." }

    Copy-Item $found.FullName -Destination $msdfgenPath -Force
    return $msdfgenPath
}

function Read-ViewBox {
    param([string]$Path)

    [xml]$xml = Get-Content -LiteralPath $Path -Raw
    $root = $xml.DocumentElement
    $viewBox = $root.GetAttribute("viewBox")
    if ($viewBox) {
        $parts = [regex]::Split($viewBox.Trim(), "[,\s]+") | Where-Object { $_ -ne "" }
        if ($parts.Count -eq 4) {
            return @{
                X = [double]::Parse($parts[0], [Globalization.CultureInfo]::InvariantCulture)
                Y = [double]::Parse($parts[1], [Globalization.CultureInfo]::InvariantCulture)
                W = [double]::Parse($parts[2], [Globalization.CultureInfo]::InvariantCulture)
                H = [double]::Parse($parts[3], [Globalization.CultureInfo]::InvariantCulture)
            }
        }
    }

    $w = $root.GetAttribute("width") -replace "[^0-9.+-]", ""
    $h = $root.GetAttribute("height") -replace "[^0-9.+-]", ""
    $width = if ($w) { [double]::Parse($w, [Globalization.CultureInfo]::InvariantCulture) } else { 24.0 }
    $height = if ($h) { [double]::Parse($h, [Globalization.CultureInfo]::InvariantCulture) } else { 24.0 }
    return @{ X = 0.0; Y = 0.0; W = $width; H = $height }
}

function Write-Metadata {
    param(
        [string]$Path,
        [string]$SourceRelative,
        [string]$TextureRelative,
        [hashtable]$ViewBox
    )

    $metadata = [ordered]@{
        type = $Type
        width = $Dimension
        height = $Dimension
        pxRange = $PxRange
        yOrigin = "top"
        source = "combatant:svg/$SourceRelative"
        texture = "combatant:svg/msdf/$TextureRelative"
        viewBox = @($ViewBox.X, $ViewBox.Y, $ViewBox.W, $ViewBox.H)
    }
    $json = $metadata | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText($Path, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

if (-not (Test-Path $inputRootPath)) {
    Write-Host "SVG input root not found: $inputRootPath" -ForegroundColor Yellow
    exit 0
}

try {
    $msdfgen = Get-Msdfgen
} catch {
    if ($Strict) { throw }
    Write-Host "msdfgen unavailable; skipping SVG MSDF generation: $($_.Exception.Message)" -ForegroundColor Yellow
    exit 0
}

if (Test-Path $outRootPath) {
    Remove-Item -LiteralPath $outRootPath -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $outRootPath | Out-Null
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null

$svgs = Get-ChildItem -Path $inputRootPath -Filter "*.svg" -Recurse
$inputRootFull = [System.IO.Path]::GetFullPath($inputRootPath).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$generated = 0
$skipped = 0

foreach ($svg in $svgs) {
    $fileFull = [System.IO.Path]::GetFullPath($svg.FullName)
    $relative = $fileFull.Substring($inputRootFull.Length).Replace('\', '/')
    if ($relative.StartsWith("msdf/")) { continue }

    $relativeNoExt = $relative.Substring(0, $relative.Length - 4)
    $outPng = Join-Path $outRootPath ($relativeNoExt + ".png")
    $outJson = Join-Path $outRootPath ($relativeNoExt + ".json")
    $outlineSvg = Join-Path $workRoot ($relativeNoExt.Replace("/", "_") + ".outline.svg")

    New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($outPng)) | Out-Null
    New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($outlineSvg)) | Out-Null

    try {
        $viewBox = Read-ViewBox $svg.FullName
        if ($viewBox.W -le 0 -or $viewBox.H -le 0) { throw "invalid viewBox" }

        $preprocessOutput = & $JavaExe -cp $PreprocessorClasspath combatant.client.render.engine.svg.SvgMsdfPreprocessor $svg.FullName $outlineSvg 2>&1
        if ($LASTEXITCODE -ne 0) { throw "svg preprocessor failed: $preprocessOutput" }
        if (-not (Test-Path $outlineSvg)) { throw "outline svg was not produced" }

        $scale = [Math]::Min($Dimension / $viewBox.W, $Dimension / $viewBox.H)
        $translateX = -$viewBox.X
        $translateY = -$viewBox.Y

        $msdfgenOutput = & $msdfgen $Type -svg $outlineSvg -o $outPng -dimensions $Dimension $Dimension -pxrange $PxRange -scale $scale -translate $translateX $translateY 2>&1
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $outPng)) { throw "msdfgen failed: $msdfgenOutput" }

        Write-Metadata $outJson $relative ($relativeNoExt + ".png") $viewBox
        $generated++
    } catch {
        $skipped++
        $message = "Skipped ${relative}: $($_.Exception.Message)"
        if ($Strict) { throw $message }
        Write-Host $message -ForegroundColor Yellow
    }
}

Write-Host "Generated $generated SVG MSDF assets, skipped $skipped." -ForegroundColor Green
