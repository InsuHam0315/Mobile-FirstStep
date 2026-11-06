# Run with the existing installed APKs; this intentionally changes runtime permissions.
# Example: ./scripts/verify-h01-permissions.ps1 -Serial emulator-5554 -State Denied
param(
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$Serial,
    [Parameter(Mandatory=$true)][Alias('PermissionState')][ValidateSet('Granted','Denied')][string]$State,
    [string]$EvidencePrefix = 'permission-isolation',
    [ValidateSet('1','2')][string]$Orientation,
    [string]$FontScale
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "adb not found: $adb" }
$package = 'com.example.firststep'
$permissions = @('android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION','android.permission.CAMERA')
function Invoke-Adb([string[]]$AdbArgs) {
    $output = & $adb -s $Serial @AdbArgs 2>&1
    $code = $LASTEXITCODE
    $output | ForEach-Object { Write-Host $_ }
    if ($code -ne 0) { throw "adb failed ($code): $($AdbArgs -join ' ')" }
    return ($output -join "`n")
}
function Assert-Idle {
    # Android 15 AOSP ProcessList.dumpProcessesLocked emits this header and calls
    # ActivityManagerService.dumpActiveInstruments without a package filter.
    $dump = Invoke-Adb @('shell','dumpsys','activity','processes')
    if ($dump -notmatch '(?m)^ACTIVITY MANAGER RUNNING PROCESSES \(dumpsys activity processes\)\s*$' -or
        $dump -notmatch '(?m)^\s*mForceBackgroundCheck=(true|false)\s*$' -or $dump -match '(?im)^\s*(Active instrumentation:|Instrumentation #)') {
        throw 'Cannot prove instrumentation is idle: active instrumentation or unrecognized/truncated process dump.'
    }
}
function Assert-Permissions([string]$Expected) {
    $dump = Invoke-Adb @('shell','dumpsys','package',$package)
    # Explicitly require a single installed user 0 block and its runtime entries.
    $users = @([regex]::Matches($dump, '(?ms)^    User 0:([^\r\n]*)(.*?)(?=^    User \d+:|^\S|\z)') | Where-Object { $_.Groups[1].Value -match '\binstalled=true\b' })
    if ($users.Count -ne 1) { throw 'Cannot identify installed package user 0.' }
    $block = $users[0].Value
    foreach ($permission in $permissions) {
        $pattern = '(?m)^\s+' + [regex]::Escape($permission) + ': granted=(true|false), flags=\['
        $found = [regex]::Matches($block, $pattern)
        if ($found.Count -ne 1 -or $found[0].Groups[1].Value -ne $Expected) { throw "Cannot prove $permission granted=$Expected for user 0." }
        Write-Host "Verified user 0 $permission granted=$Expected"
    }
}
$user = (Invoke-Adb @('shell','am','get-current-user')).Trim()
if ($user -ne '0') { throw "Only current user 0 is supported; observed '$user'." }
$ordered = if ($State -eq 'Granted') { $permissions } else { @($permissions[1],$permissions[0],$permissions[2]) }
$action = if ($State -eq 'Granted') { 'grant' } else { 'revoke' }
$expected = if ($State -eq 'Granted') { 'true' } else { 'false' }
foreach ($permission in $ordered) {
    Assert-Idle
    $null = Invoke-Adb @('shell','pm',$action,'--user','0',$package,$permission)
}
Assert-Permissions $expected
Assert-Idle
$argsForRunner = @('shell','am','instrument','-w','-r','-e','class','com.example.firststep.game.GamePreviewSmokeTest','-e','h01EvidencePrefix',$EvidencePrefix)
if ($Orientation) { $argsForRunner += @('-e','h01Orientation',$Orientation) }
if ($FontScale) { $argsForRunner += @('-e','h01FontScale',$FontScale) }
$argsForRunner += 'com.example.firststep.test/androidx.test.runner.AndroidJUnitRunner'
try {
    $result = Invoke-Adb $argsForRunner
    $statusBlocks = [regex]::Matches($result, '(?ms)INSTRUMENTATION_STATUS: (.*?)(?=^INSTRUMENTATION_STATUS_CODE: )^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$')
    $passes = @{}
    $starts = @{}
    foreach ($block in $statusBlocks) {
        $body = $block.Groups[1].Value
        $code = $block.Groups[2].Value
        $classMatch = [regex]::Match($body, '(?m)^INSTRUMENTATION_STATUS: class=(.+)\s*$')
        # First line follows the already-consumed prefix.
        if (-not $classMatch.Success) { $classMatch = [regex]::Match($body, '(?m)^class=(.+)\s*$') }
        $testMatch = [regex]::Match($body, '(?m)^(?:INSTRUMENTATION_STATUS: )?test=(\S+)\s*$')
        $numMatch = [regex]::Match($body, '(?m)^(?:INSTRUMENTATION_STATUS: )?numtests=(\d+)\s*$')
        if (-not $classMatch.Success -or $classMatch.Groups[1].Value.Trim() -ne 'com.example.firststep.game.GamePreviewSmokeTest' -or -not $testMatch.Success -or -not $numMatch.Success -or $numMatch.Groups[1].Value -ne '2') { throw 'Unrecognized test status block.' }
        $name = $testMatch.Groups[1].Value
        if ($code -eq '1') { $starts[$name] = 1 + $starts[$name] }
        elseif ($code -eq '0') { $passes[$name] = 1 + $passes[$name] }
        else { throw "Test $name reported non-pass status $code." }
    }
    $names = @('fakePreviewRestoresSelection','legacyLauncherStillOpens')
    if ($statusBlocks.Count -ne 4 -or $starts.Count -ne 2 -or $passes.Count -ne 2 -or
        @($names | Where-Object { $starts[$_] -ne 1 -or $passes[$_] -ne 1 }).Count -ne 0 -or
        $result -notmatch '(?m)^OK \(2 tests\)\s*$' -or $result -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$' -or
        $result -match '(?im)^INSTRUMENTATION_(FAILED|ABORTED|RESULT: (shortMsg|longMsg))') {
        throw 'Instrumentation did not prove exactly two successful tests.'
    }
} finally {
    Assert-Permissions $expected
}
Write-Host "PASS: both H01 tests completed with $State permissions unchanged."
