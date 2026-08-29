$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$cacheRoot = Join-Path $projectRoot '.cache'
$jdkRoot = Join-Path $cacheRoot 'jdk'
$sdkRoot = Join-Path $cacheRoot 'android-sdk'
$javaCandidates = @(Get-ChildItem -LiteralPath $jdkRoot -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
  Where-Object { $_.FullName -like '*\bin\java.exe' } |
  Select-Object -ExpandProperty FullName)
$pathJava = & where.exe java.exe 2>$null
if ($LASTEXITCODE -eq 0) { $javaCandidates += $pathJava }
$javaHome = $null
foreach ($candidate in ($javaCandidates | Select-Object -Unique)) {
  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  $version = & $candidate -version 2>&1 | Select-Object -First 1
  $ErrorActionPreference = $previousPreference
  if ($version -match 'version "(?<major>\d+)' -and [int]$Matches.major -ge 17) {
    $javaHome = Split-Path -Parent (Split-Path -Parent $candidate)
    break
  }
}
if (-not $javaHome) { throw 'JDK 17 or newer is missing. Run pnpm bootstrap:android first.' }
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $cacheRoot 'android-user-home'
$env:GRADLE_USER_HOME = Join-Path $cacheRoot 'gradle-home'

$wrapper = Join-Path $projectRoot 'android\gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper)) { throw 'Gradle wrapper is missing. Run pnpm bootstrap:android first.' }
$localGradle = Join-Path $cacheRoot 'gradle\gradle-9.5.0\bin\gradle.bat'
$gradleRunner = if (Test-Path -LiteralPath $localGradle) { $localGradle } else { $wrapper }

Push-Location (Join-Path $projectRoot 'android')
try {
  & $gradleRunner --no-daemon --stacktrace assembleDebug
  if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
} finally {
  Pop-Location
}

$sourceApk = Join-Path $projectRoot 'android\app\build\outputs\apk\debug\app-debug.apk'
$artifactDir = Join-Path $projectRoot 'artifacts\apk'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$targetApk = Join-Path $artifactDir 'dsh-mobile-v1.0.0-alpha.2-debug.apk'
Copy-Item -LiteralPath $sourceApk -Destination $targetApk -Force
Write-Host "APK: $targetApk"
