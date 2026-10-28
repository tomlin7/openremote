<#
.SYNOPSIS
    Installs the debug APK on the running emulator (or phone) and launches OpenRemote.

.DESCRIPTION
    Optionally rebuilds the APK first, installs it over the existing app, and
    starts the activity. Use -Reset to clear the saved server connection, or
    -PairCode to redeem a pairing code and connect in one step.

.PARAMETER Build
    Run `gradlew assembleDebug` before installing.

.PARAMETER Reset
    Clear app data (saved address and token) before launching.

.PARAMETER PairCode
    Pairing code from `opencode pair`. The app opens the matching
    /auth/connect/<code> link and connects immediately.

.PARAMETER Server
    Server address used for pairing links. Defaults to the emulator's host
    alias http://10.0.2.2:4096.

.EXAMPLE
    .\scripts\launch-app.ps1 -Build

.EXAMPLE
    .\scripts\launch-app.ps1 -Build -Reset -PairCode ppxp13yijrJ2t9GBtWtEFA
#>
[CmdletBinding()]
param(
    [switch]$Build,
    [switch]$Reset,
    [string]$PairCode,
    [string]$Server = "http://10.0.2.2:4096"
)

$ErrorActionPreference = "Stop"

function Resolve-AndroidSdk {
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    return (Join-Path $env:LOCALAPPDATA "Android\Sdk")
}

$root = Split-Path -Parent $PSScriptRoot
$sdk = Resolve-AndroidSdk
$adb = Join-Path $sdk "platform-tools\adb.exe"
$package = "com.tom7.openremote"

if (-not (Test-Path $adb)) {
    throw "adb not found: $adb`nSet ANDROID_HOME (or ANDROID_SDK_ROOT) to your SDK directory."
}

$attached = (& $adb devices | Select-String -Pattern "^\S+\s+device$")
if (-not $attached) {
    throw "No device is connected. Start the emulator with .\scripts\run-emulator.ps1"
}

if ($Build) {
    Write-Host "Building debug APK..."
    Push-Location $root
    try {
        & .\gradlew.bat assembleDebug --console=plain --no-configuration-cache
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }
    } finally {
        Pop-Location
    }
}

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "APK not found: $apk`nRun this script with -Build (or run gradlew assembleDebug)."
}

Write-Host "Installing $apk..."
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed." }

if ($Reset) {
    Write-Host "Clearing app data..."
    & $adb shell pm clear $package | Out-Null
}

if ($PairCode) {
    $link = "$Server/auth/connect/$PairCode"
    Write-Host "Pairing with $link"
    & $adb shell am start -a android.intent.action.VIEW -d $link $package | Out-Null
} else {
    & $adb shell am start -n "$package/.MainActivity" | Out-Null
}

$serial = (& $adb get-serialno | Out-String).Trim()
Write-Host "OpenRemote launched on $serial"
