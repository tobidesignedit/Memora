param(
  [Parameter(Mandatory = $true)]
  [int]$VersionCode,

  [Parameter(Mandatory = $true)]
  [string]$VersionName,

  [Parameter(Mandatory = $true)]
  [string]$ApkDownloadUrl,

  [string]$ReleaseNotes = "New MEMORA update available."
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "1. Updating Android versionCode=$VersionCode, versionName=$VersionName..." -ForegroundColor Cyan
$gradlePath = Join-Path $root "android\app\build.gradle.kts"
$gradle = Get-Content $gradlePath -Raw
$gradle = $gradle -replace 'versionCode\s*=\s*\d+', "versionCode = $VersionCode"
$gradle = $gradle -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$VersionName`""
Set-Content -Path $gradlePath -Value $gradle -Encoding UTF8

Write-Host "2. Building MEMORA-Android.apk..." -ForegroundColor Cyan
Push-Location (Join-Path $root "android")
.\gradlew.bat assembleDebug --no-daemon --offline
Pop-Location
Copy-Item -Force (Join-Path $root "android\app\build\outputs\apk\debug\app-debug.apk") (Join-Path $root "MEMORA-Android.apk")

Write-Host "3. Updating remote_config.json OTA metadata..." -ForegroundColor Cyan
$rcPath = Join-Path $root "remote_config.json"
$rc = Get-Content $rcPath -Raw | ConvertFrom-Json
$rc.parameters.latest_app_version_code.defaultValue.value = "$VersionCode"
$rc.parameters.latest_app_version_name.defaultValue.value = $VersionName
$rc.parameters.apk_download_url.defaultValue.value = $ApkDownloadUrl
$rc.parameters.apk_release_notes.defaultValue.value = $ReleaseNotes
$rc | ConvertTo-Json -Depth 10 | Set-Content -Path $rcPath -Encoding UTF8

Write-Host "4. Deploying Remote Config so all phones detect the update automatically..." -ForegroundColor Cyan
Push-Location $root
npx -y firebase-tools@latest deploy --only remoteconfig
Pop-Location

Write-Host "Done! Phones running MEMORA will automatically show 'MEMORA v$VersionName Ready — Download Update'." -ForegroundColor Green
