param(
    [ValidateSet('FirstStep_API35','FirstStep_AR_API33')][string]$Avd = 'FirstStep_AR_API33',
    [ValidatePattern('^(virtualscene|webcam[0-9]+)$')][string]$BackCamera = 'virtualscene',
    [ValidateRange(5554,5584)][int]$Port = 5556
)
$ErrorActionPreference = 'Stop'
if ($Port % 2 -ne 0) { throw 'Use an even emulator port.' }
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$emulatorPath = Join-Path $sdkRoot 'emulator/emulator.exe'
$adbPath = Join-Path $sdkRoot 'platform-tools/adb.exe'
$available = & $emulatorPath -list-avds
if ($available -notcontains $Avd) { throw "AVD $Avd is not installed. See README visual verification instructions." }
$running = & $adbPath devices
if ($running -match "^emulator-$Port\s") { throw "Port $Port is already in use. Close that emulator or choose another even port." }
if ($BackCamera -ne 'virtualscene') {
    $cameras = & $emulatorPath -webcam-list
    $cameras | Write-Host
    if (-not ($cameras -match [regex]::Escape($BackCamera))) { throw 'The selected webcam is not visible to the emulator. Connect the camera and start its PC app first.' }
    Write-Host 'Webcam mode tests photo capture. It is not an ARCore tracking environment.'
}
Start-Process -FilePath $emulatorPath -ArgumentList @('-avd',$Avd,'-port',"$Port",'-no-snapshot-load','-no-snapshot-save','-camera-back',$BackCamera)
Write-Host "Started visible emulator: $Avd (emulator-$Port), back camera: $BackCamera"
Write-Host 'Install the latest debug APK and open My journal > device check, or use the normal mission flow.'
