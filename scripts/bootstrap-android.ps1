$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$cacheRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot '.cache'))
$jdkRoot = Join-Path $cacheRoot 'jdk'
$gradleRoot = Join-Path $cacheRoot 'gradle'
$sdkRoot = Join-Path $cacheRoot 'android-sdk'
$downloadRoot = Join-Path $cacheRoot 'downloads'

New-Item -ItemType Directory -Force -Path $jdkRoot,$gradleRoot,$sdkRoot,$downloadRoot | Out-Null

function Find-CompatibleJavaHome {
  $candidates = @()
  $localJava = Get-ChildItem -LiteralPath $jdkRoot -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -like '*\bin\java.exe' } |
    Select-Object -ExpandProperty FullName
  $candidates += $localJava
  $pathJava = & where.exe java.exe 2>$null
  if ($LASTEXITCODE -eq 0) { $candidates += $pathJava }
  foreach ($candidate in ($candidates | Select-Object -Unique)) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $version = & $candidate -version 2>&1 | Select-Object -First 1
    $ErrorActionPreference = $previousPreference
    if ($version -match 'version "(?<major>\d+)' -and [int]$Matches.major -ge 17) {
      return Split-Path -Parent (Split-Path -Parent $candidate)
    }
  }
  return $null
}

function Get-Archive([string]$Uri, [string]$Destination) {
  if ((Test-Path -LiteralPath $Destination) -and (Get-Item -LiteralPath $Destination).Length -gt 0) {
    try {
      $archive = [System.IO.Compression.ZipFile]::OpenRead($Destination)
      $archive.Dispose()
      return
    } catch {
      Write-Host "Discarding incomplete archive $Destination"
      Remove-Item -LiteralPath $Destination -Force
    }
  }
  if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Force }
  Write-Host "Downloading $Uri"
  for ($attempt = 1; $attempt -le 3; $attempt++) {
    try {
      Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $Destination
      $archive = [System.IO.Compression.ZipFile]::OpenRead($Destination)
      $archive.Dispose()
      return
    } catch {
      if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Force }
      if ($attempt -eq 3) { throw "download failed after $attempt attempts: $Uri`n$($_.Exception.Message)" }
      Write-Host "Download attempt $attempt failed; retrying."
      Start-Sleep -Seconds 2
    }
  }
}

$jdkArchive = Join-Path $downloadRoot 'temurin-jdk17-windows-x64.zip'
$javaHome = Find-CompatibleJavaHome
if (-not $javaHome) {
  Get-Archive 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $jdkArchive
  Expand-Archive -LiteralPath $jdkArchive -DestinationPath $jdkRoot -Force
  $javaHome = Find-CompatibleJavaHome
}
if (-not $javaHome) { throw 'JDK 17 or newer bootstrap failed' }
$env:JAVA_HOME = $javaHome
Write-Host "Using Java from $javaHome"

$gradleArchive = Join-Path $downloadRoot 'gradle-9.5.0-bin.zip'
$gradleCommand = Join-Path $gradleRoot 'gradle-9.5.0\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradleCommand)) {
  Get-Archive 'https://services.gradle.org/distributions/gradle-9.5.0-bin.zip' $gradleArchive
  Expand-Archive -LiteralPath $gradleArchive -DestinationPath $gradleRoot -Force
}

$toolsArchive = Join-Path $downloadRoot 'commandlinetools-win-15859902_latest.zip'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
  Get-Archive 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' $toolsArchive
  $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $toolsArchive).Hash.ToLowerInvariant()
  $expectedHash = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
  if ($actualHash -ne $expectedHash) { throw "Android command-line tools checksum mismatch: $actualHash" }
  $stage = Join-Path $cacheRoot 'android-tools-stage'
  $resolvedStage = [System.IO.Path]::GetFullPath($stage)
  if (-not $resolvedStage.StartsWith($cacheRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'invalid Android tools stage' }
  if (Test-Path -LiteralPath $resolvedStage) { Remove-Item -LiteralPath $resolvedStage -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $resolvedStage | Out-Null
  Expand-Archive -LiteralPath $toolsArchive -DestinationPath $resolvedStage -Force
  $latest = Split-Path -Parent (Split-Path -Parent $sdkManager)
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $latest) | Out-Null
  Move-Item -LiteralPath (Join-Path $resolvedStage 'cmdline-tools') -Destination $latest
  Remove-Item -LiteralPath $resolvedStage -Recurse -Force
}

$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $cacheRoot 'android-user-home'
$env:GRADLE_USER_HOME = Join-Path $cacheRoot 'gradle-home'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
$yes = ((1..80 | ForEach-Object { 'y' }) -join [Environment]::NewLine)
$yes | & $sdkManager --sdk_root=$sdkRoot --licenses | Out-Host
$androidCli = Join-Path $sdkRoot 'cmdline-tools\latest\bin\android.exe'
$requiredSdkPaths = @(
  (Join-Path $sdkRoot 'platforms\android-37.0'),
  (Join-Path $sdkRoot 'build-tools\37.0.0'),
  (Join-Path $sdkRoot 'platform-tools')
)
if ($requiredSdkPaths.Where({ -not (Test-Path -LiteralPath $_) }).Count -gt 0) {
  & $androidCli --sdk=$sdkRoot sdk install 'platforms/android-37.0' 'build-tools/37.0.0' 'platform-tools'
}
$missingSdkPaths = $requiredSdkPaths.Where({ -not (Test-Path -LiteralPath $_) })
if ($missingSdkPaths.Count -gt 0) { throw "Android SDK install is incomplete: $($missingSdkPaths -join ', ')" }

Push-Location (Join-Path $projectRoot 'android')
try {
  & $gradleCommand wrapper --gradle-version 9.5.0 --distribution-type bin
  if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed with exit code $LASTEXITCODE" }
} finally {
  Pop-Location
}

Write-Host 'Android toolchain is ready inside .cache.'
