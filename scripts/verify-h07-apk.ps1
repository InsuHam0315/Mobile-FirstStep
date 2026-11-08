param([Parameter(Mandatory=$true)][string]$Serial,[ValidateSet('Portrait','LargeLandscape')][string]$Configuration='Portrait',[switch]$LocalSDK)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$folder=Join-Path $root ".local/harness/H07/$Configuration"
New-Item -ItemType Directory -Force $folder | Out-Null
function Adb([string[]]$ArgsForAdb){$lines=& $adb -s $Serial @ArgsForAdb 2>&1;if($LASTEXITCODE -ne 0){throw ($lines -join "`n")};return ($lines -join "`n")}
$dump=Adb @('shell','dumpsys','activity','processes')
if($dump -notmatch 'mForceBackgroundCheck=' -or $dump -match '(?im)^\s*(Active instrumentation:|Instrumentation #)'){throw 'Instrumentation not proven idle'}
$orientation=if($Configuration -eq 'Portrait'){'1'}else{'2'}
$scale=if($Configuration -eq 'Portrait'){'1.0'}else{'1.6'}
$rotation=if($Configuration -eq 'Portrait'){'0'}else{'1'}
$null=Adb @('shell','settings','put','system','accelerometer_rotation','0')
$null=Adb @('shell','settings','put','system','user_rotation',$rotation)
$null=Adb @('shell','settings','put','system','font_scale',$scale)
$classes='com.example.firststep.game.identity.IdentityFakeSmokeTest,com.example.firststep.game.identity.IdentityConfigurationSmokeTest#cleanUnconfiguredRouteNeverStartsMockLogin'
$names=@('missingProfileNeedsServerConfirmedSave','returningAndIncompleteProfilesRouteCorrectly','canceledLoginAndFailedSaveRetainDraftForRetry','logoutAndSwitchRejectLateCallbacks','recreationPreservesSameAccountDraftAndClearsOnSwitch','cleanUnconfiguredRouteNeverStartsMockLogin')
function Run-Instrumentation([string]$ClassFilter,[string[]]$ExpectedNames,[string]$LogName){
 $idle=Adb @('shell','dumpsys','activity','processes')
 if($idle -notmatch 'mForceBackgroundCheck=' -or $idle -match '(?im)^\s*(Active instrumentation:|Instrumentation #)'){throw 'Instrumentation not proven idle'}
 $argsForRunner=@('shell','am','instrument','-w','-r','-e','class',$ClassFilter,'-e','h07EvidencePrefix',$Configuration,'-e','h07Orientation',$orientation,'-e','h07FontScale',$scale,'com.example.firststep.test/androidx.test.runner.AndroidJUnitRunner')
 Write-Host "adb -s $Serial $($argsForRunner -join ' ')"
 $result=Adb $argsForRunner
 $result|Set-Content (Join-Path $folder $LogName)
 $blocks=[regex]::Matches($result,'(?ms)INSTRUMENTATION_STATUS: (.*?)(?=^INSTRUMENTATION_STATUS_CODE: )^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$')
 $starts=@{};$passes=@{}
 foreach($block in $blocks){
  $body=$block.Groups[1].Value;$code=$block.Groups[2].Value
  $test=[regex]::Match($body,'(?m)^(?:INSTRUMENTATION_STATUS: )?test=(\S+)\s*$')
  $count=[regex]::Match($body,'(?m)^(?:INSTRUMENTATION_STATUS: )?numtests=(\d+)\s*$')
  $class=[regex]::Match($body,'(?m)^(?:INSTRUMENTATION_STATUS: )?class=(\S+)\s*$')
  if(-not $test.Success -or $test.Groups[1].Value -notin $ExpectedNames -or -not $count.Success -or [int]$count.Groups[1].Value -ne $ExpectedNames.Count -or -not $class.Success -or $class.Groups[1].Value -notmatch '^com[.]example[.]firststep[.]game[.]identity[.]Identity(Fake|Configuration|Emulator)SmokeTest$'){throw "Unexpected test status in $LogName"}
  $name=$test.Groups[1].Value
  if($code -eq '1'){$starts[$name]=1+$starts[$name]}
  elseif($code -eq '0'){$passes[$name]=1+$passes[$name]}
  else{throw "Non-pass/skipped status $code for $name in $LogName"}
 }
 if($blocks.Count -ne 2*$ExpectedNames.Count -or @($ExpectedNames|Where-Object{$starts[$_] -ne 1 -or $passes[$_] -ne 1}).Count -ne 0 -or $result -notmatch ('(?m)^OK \('+$ExpectedNames.Count+' tests?\)\s*$') -or $result -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$' -or $result -match '(?im)^INSTRUMENTATION_(FAILED|ABORTED|RESULT: (shortMsg|longMsg))'){throw "No complete successful result in $LogName"}
 Write-Host "PASS: $($ExpectedNames.Count) named tests in $LogName, no skips."
}
if($LocalSDK){
 # Disruptive transport fixture gets a separate instrumentation process FIRST.
 # No app-data reset; every named method still executes and is counted explicitly.
 $outage='sdkUnavailableTransportCannotCompleteAndRetryConfirmsServer'
 Run-Instrumentation "com.example.firststep.game.identity.IdentityEmulatorSmokeTest#$outage" @($outage) 'transport.log'
 $sdkNames=@('sdkServerProfileSurvivesFreshControllerAndRejectsOtherUid','sdkMalformedProfileCannotBeEditedOrOverwritten','sdkConcurrentCompleteProfileIsCanonicalWithoutOverwrite','heldSdkCompletionCannotResurrectLoggedOutAccount')
 foreach($name in $sdkNames){$classes+=",com.example.firststep.game.identity.IdentityEmulatorSmokeTest#$name"}
 $names+=$sdkNames
}
Run-Instrumentation $classes $names 'focused.log'
$target=Join-Path $folder 'screenshots';New-Item -ItemType Directory -Force $target|Out-Null
$remote='/sdcard/Android/data/com.example.firststep/files/h07-evidence'
foreach($name in ((Adb @('shell','ls',$remote)) -split "`n")){$name=$name.Trim();if($name.StartsWith("$Configuration-") -and $name -match '^[A-Za-z0-9_-]+[.]png$'){$null=Adb @('pull',"$remote/$name",(Join-Path $target $name))}}
$total=$names.Count+$(if($LocalSDK){1}else{0})
$invocations=if($LocalSDK){2}else{1}
Write-Host "PASS: $total unique H07 tests across $invocations instrumentation invocations, no skips. Fake/local-emulator identity only; Google OAuth unverified."
