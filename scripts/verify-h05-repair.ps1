# Focused management P2 regressions. Run after the existing H05 runner prepares the dedicated device.
param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$EvidencePrefix='h05-repair',
    [ValidateSet('1','2')][string]$Orientation='1',
    [string]$FontScale='1.0'
)
$ErrorActionPreference='Stop'
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
function Adb([string[]]$Arguments){
    $out=& $adb -s $Serial @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw "adb failed: $out"}
    return ($out -join "`n")
}
if((Adb @('shell','getprop','ro.build.version.sdk')).Trim() -ne '35' -or
   (Adb @('shell','am','get-current-user')).Trim() -ne '0'){throw 'API35/user0 required'}
$idle=Adb @('shell','dumpsys','activity','processes')
if($idle -notmatch 'mForceBackgroundCheck=' -or $idle -match '(?im)^\s*(Active instrumentation:|Instrumentation #)'){throw 'Cannot prove instrumentation idle'}
$result=Adb @('shell','am','instrument','-w','-r','-e','class','com.example.firststep.facility.FacilityRepositoryAtomicityTest,com.example.firststep.game.home.MapAttributionTest','-e','h05EvidencePrefix',$EvidencePrefix,'-e','h05Orientation',$Orientation,'-e','h05FontScale',$FontScale,'com.example.firststep.test/androidx.test.runner.AndroidJUnitRunner')
Write-Output $result
$codes=@([regex]::Matches($result,'(?m)^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$') | ForEach-Object{$_.Groups[1].Value})
foreach($name in @('legacyMainAndWorkerRepositoryWritesPreserveEachOther','creditStaysInsideVisibleMapAcrossOuterScroll')){
    if([regex]::Matches($result,"(?m)^INSTRUMENTATION_STATUS: test=$name\s*$").Count -ne 2){throw "Missing named check $name"}
}
if($codes.Count -ne 4 -or @($codes|Where-Object{$_ -eq '0'}).Count -ne 2 -or
   @($codes|Where-Object{$_ -eq '1'}).Count -ne 2 -or $result -notmatch '(?m)^OK \(2 tests\)\s*$' -or
   $result -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$'){throw 'Expected two successful tests, no skips/errors'}
Write-Output 'PASS: two management repair regressions; no permission setup during instrumentation.'
