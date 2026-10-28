<#
.SYNOPSIS
    Boots the OpenRemote Android emulator and waits until it is ready.

.DESCRIPTION
    Starts the named AVD, waits for Android to finish booting, and dismisses the
    keyguard so `scripts/launch-app.ps1` can start the app immediately.

    The emulator window stays open unless -Headless is supplied. Use -ColdBoot
    when a saved snapshot looks stale.

.PARAMETER Avd
    AVD name. Defaults to "openremote".

.PARAMETER Headless
    Run without a window (useful on CI). Uses the software GPU.

.PARAMETER ColdBoot
    Ignore the saved snapshot and boot from scratch.

.EXAMPLE
    .\scripts\run-emulator.ps1

.EXAMPLE
    .\scripts\run-emulator.ps1 -Avd openremote -Headless -ColdBoot
#>
[CmdletBinding()]
param(
    [string]$Avd = "openremote",
    [switch]$Headless,
    [switch]$ColdBoot
)

$ErrorActionPreference = "Stop"

function Resolve-AndroidSdk {
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    return (Join-Path $env:LOCALAPPDATA "Android\Sdk")
}

$sdk = Resolve-AndroidSdk
$emulator = Join-Path $sdk "emulator\emulator.exe"
$adb = Join-Path $sdk "platform-tools\adb.exe"

foreach ($tool in @($emulator, $adb)) {
    if (-not (Test-Path $tool)) {
        throw "Android SDK tool not found: $tool`nSet ANDROID_HOME (or ANDROID_SDK_ROOT) to your SDK directory."
    }
}

$avdDirectory = Join-Path $env:USERPROFILE ".android\avd\$Avd.avd"
if (-not (Test-Path $avdDirectory)) {
    throw "AVD '$Avd' does not exist. Create it once with:`n  sdkmanager `"system-images;android-36;default;x86_64`"`n  avdmanager create avd -n $Avd -k `"system-images;android-36;default;x86_64`" -d pixel_6"
}

$arguments = @("-avd", $Avd, "-no-audio")
if ($Headless) { $arguments += @("-no-window", "-gpu", "swiftshader_indirect") }
if ($ColdBoot) { $arguments += "-no-snapshot-load" }

Write-Host "Starting emulator '$Avd'..."
Start-Process -FilePath $emulator -ArgumentList $arguments

& $adb wait-for-device
for ($attempt = 0; $attempt -lt 150; $attempt++) {
    $booted = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($booted -eq "1") { break }
    if ($attempt -eq 149) { throw "The emulator did not finish booting. Check the emulator window or run with -ColdBoot." }
    Start-Sleep -Seconds 2
}

& $adb shell wm dismiss-keyguard 2>$null | Out-Null
& $adb shell input keyevent 82 2>$null | Out-Null

$serial = (& $adb get-serialno | Out-String).Trim()
Write-Host "Emulator ready: $serial"
Write-Host "Next: .\scripts\launch-app.ps1 -Build"
