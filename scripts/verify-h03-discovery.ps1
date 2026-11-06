# Dedicated API35/user-0 scenario runner. Prepare permissions before instrumentation only.
param(
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$Serial,
    [Parameter(Mandatory=$true)][ValidateSet('Granted','Denied')][string]$State,
    [string]$EvidencePrefix = 'discovery',
    [ValidateSet('1','2')][string]$Orientation = '1',
    [string]$FontScale = '1.0'
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "adb not found: $adb" }
function Invoke-Adb([string[]]$AdbArgs) {
    $output = & $adb -s $Serial @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($AdbArgs -join ' ')`n$output" }
    return ($output -join "`n")
}
function Assert-Idle {
    $dump = Invoke-Adb @('shell','dumpsys','activity','processes')
    if ($dump -notmatch '(?m)^ACTIVITY MANAGER RUNNING PROCESSES \(dumpsys activity processes\)\s*$' -or
        $dump -notmatch '(?m)^\s*mForceBackgroundCheck=(true|false)\s*$' -or
        $dump -match '(?im)^\s*(Active instrumentation:|Instrumentation #)') {
        throw 'Cannot prove instrumentation is idle; refusing permission setup.'
    }
    Write-Host 'Verified idle preflight (point-in-time; no concurrent external launcher allowed).'
}
function Assert-Camera([string]$Expected) {
    $dump = Invoke-Adb @('shell','dumpsys','package','com.example.firststep')
    $users = @([regex]::Matches($dump, '(?ms)^    User 0:([^\r\n]*)(.*?)(?=^    User \d+:|^\S|\z)') | Where-Object { $_.Groups[1].Value -match '\binstalled=true\b' })
    if ($users.Count -ne 1) { throw 'Cannot identify installed user 0.' }
    $camera = [regex]::Matches($users[0].Value, '(?m)^\s+android\.permission\.CAMERA: granted=(true|false), flags=\[')
    if ($camera.Count -ne 1 -or $camera[0].Groups[1].Value -ne $Expected) { throw "Cannot prove CAMERA granted=$Expected." }
    Write-Host "Verified user 0 CAMERA granted=$Expected"
}
if ((Invoke-Adb @('shell','am','get-current-user')).Trim() -ne '0') { throw 'Only current user 0 is supported.' }
Assert-Idle
$action = if ($State -eq 'Granted') { 'grant' } else { 'revoke' }
$expected = if ($State -eq 'Granted') { 'true' } else { 'false' }
$cameraExpectation = if ($State -eq 'Granted') { 'STREAMING' } else { 'DENIED' }
$null = Invoke-Adb @('shell','pm',$action,'--user','0','com.example.firststep','android.permission.CAMERA')
Assert-Camera $expected
Assert-Idle
try {
    $result = Invoke-Adb @('shell','am','instrument','-w','-r','-e','class','com.example.firststep.game.discovery.DiscoverySmokeTest','-e','h03ExpectedCamera',$cameraExpectation,'-e','h03Orientation',$Orientation,'-e','h03FontScale',$FontScale,'-e','h03EvidencePrefix',$EvidencePrefix,'com.example.firststep.test/androidx.test.runner.AndroidJUnitRunner')
    Write-Host $result
    $blocks = [regex]::Matches($result, '(?ms)INSTRUMENTATION_STATUS: (.*?)(?=^INSTRUMENTATION_STATUS_CODE: )^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$')
    $starts = @{}; $passes = @{}
    foreach ($block in $blocks) {
        $body = $block.Groups[1].Value
        $test = [regex]::Match($body, '(?m)^(?:INSTRUMENTATION_STATUS: )?test=(\S+)\s*$')
        if ($body -notmatch '(?m)^(?:INSTRUMENTATION_STATUS: )?class=com\.example\.firststep\.game\.discovery\.DiscoverySmokeTest\s*$' -or
            $body -notmatch '(?m)^(?:INSTRUMENTATION_STATUS: )?numtests=6\s*$' -or -not $test.Success) { throw 'Unexpected instrumentation status block.' }
        $name = $test.Groups[1].Value
        switch ($block.Groups[2].Value) {
            '1' { $starts[$name] = 1 + $starts[$name] }
            '0' { $passes[$name] = 1 + $passes[$name] }
            default { throw "Test $name did not pass (including ignored/assumption statuses)." }
        }
    }
    $names = @('simulatedShakeAndRecreationRejectOldSession','simulatedCoverSupportsStableOnChangeSample','cameraFailureAndUnusableLightHaveCancelableFallback','pauseCancelsHeldInputAndRequiresFreshArrival','actualCameraMatchesInitialPermissionAndHasFallback','legacyLauncherStillOpens')
    if ($blocks.Count -ne 12 -or $starts.Count -ne 6 -or $passes.Count -ne 6 -or
        @($names | Where-Object { $starts[$_] -ne 1 -or $passes[$_] -ne 1 }).Count -ne 0 -or
        $result -notmatch '(?m)^OK \(6 tests\)\s*$' -or $result -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$' -or
        $result -match '(?im)^INSTRUMENTATION_(FAILED|ABORTED|RESULT: (shortMsg|longMsg))') { throw 'Expected six complete successful tests, no skips.' }
} finally { Assert-Camera $expected }
Write-Host "PASS: six H03 tests; $State camera permission unchanged; expected camera=$cameraExpectation."
