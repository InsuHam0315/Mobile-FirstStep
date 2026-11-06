# Dedicated API35/current-user-0 device only. Never change permissions during instrumentation.
param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [ValidateSet('Denied','Approximate','Fine')][string]$LocationPermission='Denied',
    [string]$EvidencePrefix='home',
    [ValidateSet('1','2')][string]$Orientation='1',
    [string]$FontScale='1.0'
)
$ErrorActionPreference='Stop'
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
function Invoke-Adb([string[]]$Arguments) {
    $out=& $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')`n$out" }
    return ($out -join "`n")
}
function Assert-Idle {
    $dump=Invoke-Adb @('shell','dumpsys','activity','processes')
    if ($dump -notmatch '(?m)^ACTIVITY MANAGER RUNNING PROCESSES' -or
        $dump -notmatch 'mForceBackgroundCheck=(true|false)' -or
        $dump -match '(?im)^\s*(Active instrumentation:|Instrumentation #)') {
        throw 'Cannot prove instrumentation idle; refusing setup.'
    }
}
function Permission-State {
    $dump=Invoke-Adb @('shell','dumpsys','package','com.example.firststep')
    $users=@([regex]::Matches($dump,'(?ms)^    User 0:([^\r\n]*)(.*?)(?=^    User \d+:|^\S|\z)') | Where-Object {$_.Groups[1].Value -match '\binstalled=true\b'})
    if ($users.Count -ne 1) { throw 'Cannot identify installed user 0' }
    $values=@{}
    foreach ($permission in @('ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','CAMERA','POST_NOTIFICATIONS')) {
        $matches=[regex]::Matches($users[0].Value,"(?m)^\s+android\.permission\.${permission}: granted=(true|false), flags=\[")
        if ($matches.Count -ne 1) { throw "Cannot prove permission $permission" }
        $values[$permission]=$matches[0].Groups[1].Value
    }
    return $values
}
if ((Invoke-Adb @('shell','getprop','ro.build.version.sdk')).Trim() -ne '35' -or
    (Invoke-Adb @('shell','am','get-current-user')).Trim() -ne '0') { throw 'Runner supports API35/user0 only.' }
Assert-Idle
$before=Permission-State
$null=Invoke-Adb @('shell','pm','revoke','--user','0','com.example.firststep','android.permission.ACCESS_FINE_LOCATION')
$coarse=if ($LocationPermission -eq 'Denied') {'revoke'} else {'grant'}
$null=Invoke-Adb @('shell','pm',$coarse,'--user','0','com.example.firststep','android.permission.ACCESS_COARSE_LOCATION')
if ($LocationPermission -eq 'Fine') {
    $null=Invoke-Adb @('shell','pm','grant','--user','0','com.example.firststep','android.permission.ACCESS_FINE_LOCATION')
}
$prepared=Permission-State
$fineExpected=if ($LocationPermission -eq 'Fine') {'true'} else {'false'}
$coarseExpected=if ($LocationPermission -eq 'Denied') {'false'} else {'true'}
if ($prepared.ACCESS_FINE_LOCATION -ne $fineExpected -or $prepared.ACCESS_COARSE_LOCATION -ne $coarseExpected) { throw 'Permission preflight mismatch.' }
Assert-Idle
Write-Output "Prepared $LocationPermission before instrumentation (point-in-time idle check; no concurrent launcher permitted)."
$result=Invoke-Adb @('shell','am','instrument','-w','-r','-e','class','com.example.firststep.game.home.HomeSmokeTest,com.example.firststep.game.home.HomeLocationAdapterTest','-e','h05Orientation',$Orientation,'-e','h05FontScale',$FontScale,'-e','h05EvidencePrefix',$EvidencePrefix,'com.example.firststep.test/androidx.test.runner.AndroidJUnitRunner')
Write-Output $result
$codes=@([regex]::Matches($result,'(?m)^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$') | ForEach-Object {$_.Groups[1].Value})
$names=@('searchDetailFavoriteAndRecreationPreserveLegacyIdentity','mapProfileEndingAndSimulatedLocationRemainHonest','homeRefreshReadsSameH04TargetAndSeparatesModes','injectedFreshStaleAndPauseClearIndicators','corruptProgressShowsRetryWithoutErasingRecord','permissionResultWaitsForResumeAndIgnoresOtherScreen')
foreach ($name in $names) {
    if ([regex]::Matches($result,"(?m)^INSTRUMENTATION_STATUS: test=$name\s*$").Count -ne 2) { throw "Missing named check: $name" }
}
if ($codes.Count -ne 12 -or @($codes | Where-Object {$_ -eq '0'}).Count -ne 6 -or
    @($codes | Where-Object {$_ -eq '1'}).Count -ne 6 -or
    $result -notmatch '(?m)^OK \(6 tests\)\s*$' -or
    $result -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$') { throw 'Expected six completed tests, no skips/errors.' }
$after=Permission-State
foreach ($permission in $prepared.Keys) {
    if ($after[$permission] -ne $prepared[$permission]) { throw "Instrumentation changed $permission" }
}
foreach ($permission in @('CAMERA','POST_NOTIFICATIONS')) {
    if ($after[$permission] -ne $before[$permission]) { throw "Setup changed unrelated $permission" }
}
Write-Output 'PASS: six named H05 checks; permission grants unchanged during instrumentation.'
