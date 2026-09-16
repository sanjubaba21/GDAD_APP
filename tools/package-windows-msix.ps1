[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApplicationDirectory,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9.-]{3,50}$')]
    [string]$IdentityName,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^CN=.+')]
    [string]$Publisher,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$PublisherDisplayName,

    [ValidatePattern('^\d{1,5}\.\d{1,5}\.\d{1,5}\.\d{1,5}$')]
    [string]$Version = '0.1.0.0',

    [ValidateSet('x64', 'x86', 'arm64')]
    [string]$Architecture = 'x64',

    [string]$MakeAppxPath,

    [switch]$StageOnly
)

$ErrorActionPreference = 'Stop'

function ConvertTo-XmlText([string]$Value) {
    return [System.Security.SecurityElement]::Escape($Value)
}

function Resolve-MakeAppx([string]$RequestedPath) {
    if ($RequestedPath) {
        $resolved = Resolve-Path -LiteralPath $RequestedPath -ErrorAction Stop
        return $resolved.Path
    }

    $command = Get-Command 'makeappx.exe' -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $windowsKits = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
    if (Test-Path -LiteralPath $windowsKits) {
        $candidate = Get-ChildItem -LiteralPath $windowsKits -Recurse -Filter 'makeappx.exe' |
            Where-Object { $_.FullName -match '\\x64\\makeappx\.exe$' } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($candidate) { return $candidate.FullName }
    }

    throw 'MakeAppx.exe was not found. Install the Windows SDK or run this package task on windows-latest.'
}

function Write-SquarePng([System.Drawing.Image]$Source, [int]$Size, [string]$Path) {
    $image = [System.Drawing.Bitmap]::new($Size, $Size)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($image)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.DrawImage($Source, 0, 0, $Size, $Size)
        } finally {
            $graphics.Dispose()
        }
        $image.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $image.Dispose()
    }
}

$application = (Resolve-Path -LiteralPath $ApplicationDirectory -ErrorAction Stop).Path
$launcher = Join-Path $application 'GDAD BAGS.exe'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "The packaged GDAD BAGS launcher is missing: $launcher"
}

$versionParts = $Version.Split('.') | ForEach-Object { [int]$_ }
if ($versionParts | Where-Object { $_ -gt 65535 }) {
    throw 'Every MSIX version component must be between 0 and 65535.'
}

$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $outputFullPath
if (-not $outputDirectory) { $outputDirectory = (Get-Location).Path }
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$staging = Join-Path $outputDirectory 'gdad-bags-msix-staging'
$stagingFullPath = [System.IO.Path]::GetFullPath($staging)
$outputDirectoryFullPath = [System.IO.Path]::GetFullPath($outputDirectory).TrimEnd('\') + '\'
if (-not $stagingFullPath.StartsWith($outputDirectoryFullPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to prepare an MSIX staging directory outside the selected output directory.'
}
if (Test-Path -LiteralPath $stagingFullPath) {
    Remove-Item -LiteralPath $stagingFullPath -Recurse -Force
}
New-Item -ItemType Directory -Path $stagingFullPath | Out-Null
Copy-Item -Path (Join-Path $application '*') -Destination $stagingFullPath -Recurse -Force

$assets = Join-Path $stagingFullPath 'Assets'
New-Item -ItemType Directory -Path $assets | Out-Null
Add-Type -AssemblyName System.Drawing
$sourceIcon = [System.Drawing.Icon]::ExtractAssociatedIcon($launcher)
if (-not $sourceIcon) { throw 'The GDAD BAGS launcher icon could not be read.' }
try {
    $sourceBitmap = $sourceIcon.ToBitmap()
    try {
        Write-SquarePng $sourceBitmap 44 (Join-Path $assets 'Square44x44Logo.png')
        Write-SquarePng $sourceBitmap 50 (Join-Path $assets 'StoreLogo.png')
        Write-SquarePng $sourceBitmap 150 (Join-Path $assets 'Square150x150Logo.png')
    } finally {
        $sourceBitmap.Dispose()
    }
} finally {
    $sourceIcon.Dispose()
}

$identityXml = ConvertTo-XmlText $IdentityName
$publisherXml = ConvertTo-XmlText $Publisher
$publisherDisplayXml = ConvertTo-XmlText $PublisherDisplayName
$manifest = @"
<?xml version="1.0" encoding="utf-8"?>
<Package
  xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
  xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
  xmlns:uap10="http://schemas.microsoft.com/appx/manifest/uap/windows10/10"
  xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
  IgnorableNamespaces="uap uap10 rescap">
  <Identity Name="$identityXml" Publisher="$publisherXml" Version="$Version" ProcessorArchitecture="$Architecture" />
  <Properties>
    <DisplayName>GDAD BAGS</DisplayName>
    <PublisherDisplayName>$publisherDisplayXml</PublisherDisplayName>
    <Logo>Assets\StoreLogo.png</Logo>
  </Properties>
  <Resources>
    <Resource Language="en-us" />
  </Resources>
  <Dependencies>
    <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.19041.0" MaxVersionTested="10.0.26100.0" />
  </Dependencies>
  <Applications>
    <Application
      Id="GdadBags"
      Executable="GDAD BAGS.exe"
      uap10:RuntimeBehavior="packagedClassicApp"
      uap10:TrustLevel="mediumIL">
      <uap:VisualElements
        DisplayName="GDAD BAGS"
        Description="Sales, stock, vendor, cash and reporting for GDAD BAGS"
        BackgroundColor="transparent"
        Square150x150Logo="Assets\Square150x150Logo.png"
        Square44x44Logo="Assets\Square44x44Logo.png" />
    </Application>
  </Applications>
  <Capabilities>
    <rescap:Capability Name="runFullTrust" />
  </Capabilities>
</Package>
"@
$manifestPath = Join-Path $stagingFullPath 'AppxManifest.xml'
$manifest | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM

try {
    $xml = [xml](Get-Content -LiteralPath $manifestPath -Raw)
    if ($xml.Package.Identity.Name -ne $IdentityName -or $xml.Package.Identity.Publisher -ne $Publisher) {
        throw 'Generated MSIX identity does not match the requested Store identity.'
    }
} catch {
    throw "Generated AppxManifest.xml is invalid: $($_.Exception.Message)"
}

if ($StageOnly) {
    Write-Output $stagingFullPath
    exit 0
}

$makeAppx = Resolve-MakeAppx $MakeAppxPath
& $makeAppx pack /d $stagingFullPath /p $outputFullPath /o
if ($LASTEXITCODE -ne 0) { throw "MakeAppx failed with exit code $LASTEXITCODE." }
if (-not (Test-Path -LiteralPath $outputFullPath -PathType Leaf)) {
    throw 'MakeAppx completed without producing the requested MSIX.'
}

Write-Output $outputFullPath
