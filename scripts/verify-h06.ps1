param(
  [Parameter(Mandatory=$true)][string]$Serial,
  [ValidateSet('Portrait','LargeLandscape')][string]$Configuration='Portrait',
  [switch]$Restart
)
$ErrorActionPreference='Stop'
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$root=Split-Path -Parent $PSScriptRoot
$folder=Join-Path $root ".local/harness/H06/$Configuration"
New-Item -ItemType Directory -Force -Path $folder | Out-Null
function Adb([string[]]$Arguments){
  $lines=& $adb -s $Serial @Arguments 2>&1
  if($LASTEXITCODE -ne 0){throw ($lines -join "`n")}
  return ($lines -join "`n")
}
function Idle {
  $dump=Adb @('shell','dumpsys','activity','processes')
  if($dump -notmatch '(?m)^ACTIVITY MANAGER RUNNING PROCESSES' -or $dump -notmatch 'mForceBackgroundCheck=' -or $dump -match '(?im)^\s*(Active instrumentation:|Instrumentation #)') {throw 'Cannot prove instrumentation idle.'}
}
function Run([string]$Class,[string[]]$Names,[string]$Log,[string[]]$Extras=@()){
  Idle
  $a=@('shell','am','instrument','-w','-r','-e','class',$Class,'-e','h06EvidencePrefix',$Configuration,'-e','h06Orientation',$script:orientation,'-e','h06FontScale',$script:scale)+$Extras+@('com.example.firststep.test/androidx.test.runner.AndroidJUnitRunner')
  Write-Host "adb -s $Serial $($a -join ' ')"
  $result=Adb $a
  $result | Set-Content -LiteralPath (Join-Path $folder $Log)
  $codes=@([regex]::Matches($result,'(?m)^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$') | ForEach-Object {$_.Groups[1].Value})
  if($codes.Count -ne 2*$Names.Count -or @($codes|Where-Object {$_ -notin @('0','1')}).Count -ne 0){throw "Unexpected/skipped/failed statuses; see $Log"}
  foreach($name in $Names){
    if([regex]::Matches($result,'(?m)^INSTRUMENTATION_STATUS: test='+[regex]::Escape($name)+'\s*$').Count -ne 2){throw "Missing/duplicate test $name"}
  }
  if(@($codes|Where-Object {$_ -eq '0'}).Count -ne $Names.Count -or $result -notmatch ('(?m)^OK \('+ $Names.Count +' tests?\)\s*$') -or $result -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$' -or $result -match '(?im)^INSTRUMENTATION_(FAILED|ABORTED|RESULT: (shortMsg|longMsg))') {throw "No complete pass: $Log"}
  Write-Host "PASS $Log ($($Names.Count) tests, no skips)"
}
if((Adb @('shell','am','get-current-user')).Trim() -ne '0'){throw 'Only dedicated API35/user 0 supported.'}
Idle
# Explicitly prepare denial BEFORE instrumentation; never revoke target permissions within a test.
foreach($permission in @('android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION','android.permission.CAMERA')){
  Idle;$null=Adb @('shell','pm','revoke','--user','0','com.example.firststep',$permission)
}
$dump=Adb @('shell','dumpsys','package','com.example.firststep')
$dump | Set-Content (Join-Path $folder 'permissions-before.log')
foreach($permission in @('CAMERA','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION')){
  if($dump -notmatch ('android.permission.'+$permission+': granted=false')){throw "Denial not established: $permission"}
}
$script:orientation=if($Configuration -eq 'Portrait'){'1'}else{'2'}
$script:scale=if($Configuration -eq 'Portrait'){'1.0'}else{'1.6'}
$rotation=if($Configuration -eq 'Portrait'){'0'}else{'1'}
Idle
$null=Adb @('shell','settings','put','system','accelerometer_rotation','0')
$null=Adb @('shell','settings','put','system','user_rotation',$rotation)
$null=Adb @('shell','settings','put','system','font_scale',$script:scale)
$loop=@('demoLoopRetriesOneStampAndRefreshesHome','normalFakeBoundaryReopensWithoutDiscovery','cancellationAndPendingRecreationDoNotAdvance','malformedAndUnpreparedRoutesNeverOpenDefaultDb')
$boundary=@('intentContextBoundary','intentResultBoundary','navigationPersistenceAndStaleness','clockBindingAndIsolation','stableEntryRunIsolation','commitFailurePoisonsOnlyIdentity')
Run 'com.example.firststep.game.integration.IntegrationLoopTest,com.example.firststep.game.integration.IntegrationBoundaryTest' ($loop+$boundary) 'focused.log'
if($Restart){
  $run=[guid]::NewGuid().ToString()
  $extra=@('-e','h06RestartRun',$run)
  Run 'com.example.firststep.game.integration.IntegrationRestartTest#prepareRestart' @('prepareRestart') 'restart-prepare.log' $extra
  Idle
  $null=Adb @('shell','am','force-stop','com.example.firststep')
  $pidAfter=Adb @('shell','sh','-c','"pidof com.example.firststep || true"')
  if($pidAfter.Trim()){throw "Target process survived force-stop: $pidAfter"}
  "force-stop completed; target pid empty; run=$run" | Set-Content (Join-Path $folder 'restart-stop.log')
  Run 'com.example.firststep.game.integration.IntegrationRestartTest#resumeAfterRestart' @('resumeAfterRestart') 'restart-resume.log' $extra
}
$captures=Join-Path $folder 'screenshots'
New-Item -ItemType Directory -Force -Path $captures | Out-Null
$remote='/sdcard/Android/data/com.example.firststep/files/h06-evidence'
$names=(Adb @('shell','ls',$remote)) -split "`n"
foreach($name in $names){
  $name=$name.Trim()
  if($name.StartsWith("$Configuration-") -and $name -match '^[A-Za-z0-9_-]+[.]png$'){
    $null=Adb @('pull',"$remote/$name",(Join-Path $captures $name))
  }
}
$after=Adb @('shell','dumpsys','package','com.example.firststep')
$after | Set-Content (Join-Path $folder 'permissions-after.log')
foreach($permission in @('CAMERA','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION')){
  if($after -notmatch ('android.permission.'+$permission+': granted=false')){throw "Permission changed: $permission"}
}
if($Restart){
  $pids=Adb @('logcat','-d','-s','System.out:I')
  ($pids -split "`n" | Where-Object {$_ -match 'H06_RESTART_' -and $_ -match [regex]::Escape($run)}) | Set-Content (Join-Path $folder 'restart-pids.log')
}
Write-Host 'H06 focused APK checks completed. Fixture arrival/hold/fake clock; no physical/campus verification.'
