param(
    [ValidatePattern('^emulator-[0-9]+$')][string]$Serial = 'emulator-5556',
    [ValidateRange(1,300)][int]$Seconds = 30
)
$ErrorActionPreference = 'Stop'
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$adbPath = Join-Path $sdkRoot 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { throw 'Android SDK adb was not found.' }
$devices = & $adbPath devices
if (-not ($devices -match ('^' + [regex]::Escape($Serial) + '\s+device$'))) { throw 'Start the selected emulator first.' }
Write-Host 'TEST LOCATION ONLY: KSNU map center, not a surveyed facility or physical GPS fix.'
Write-Host 'Open the campus map in FirstStep, then tap My location. Ctrl+C stops updates.'
for ($step=0; $step -lt $Seconds; $step++) {
    & $adbPath -s $Serial emu geo fix 126.6802365 35.9456011 15 8 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Emulator location injection failed.' }
    Start-Sleep -Milliseconds 1000
}
Write-Host 'Updates stopped. The app removes stale position markers after 10 seconds.'
