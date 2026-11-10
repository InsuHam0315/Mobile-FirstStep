param([string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME=$JavaHome
if(-not $env:ANDROID_HOME){$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
$evidence=Join-Path $root '.local/harness/H07'
$fixture=Join-Path $evidence 'temporary-client-config.json'
$original=Join-Path $root 'app/google-services.json'
$originalHash=if(Test-Path -LiteralPath $original){(Get-FileHash -LiteralPath $original).Hash}else{$null}
# Firebase client-metadata fixture only: deliberately NO invented OAuth client ID and NO live login.
$data=@{project_info=@{project_id='demo-firststep-h07'};client=@(@{client_info=@{mobilesdk_app_id='local-config-transition-fixture';android_client_info=@{package_name='com.example.firststep'}};api_key=@(@{current_key='local-emulator-only'});oauth_client=@()})}|ConvertTo-Json -Depth 8
if(Test-Path -LiteralPath $fixture){throw 'Refusing to overwrite existing temporary fixture.'}
$stream=[IO.File]::Open($fixture,[IO.FileMode]::CreateNew)
try{$bytes=[Text.Encoding]::UTF8.GetBytes($data);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
Push-Location -LiteralPath $root
try{
 & .\gradlew.bat clean assembleDebug "-Ph07ConfigPath=$fixture" --console=plain *> (Join-Path $evidence 'config-present-build.log')
 if($LASTEXITCODE -ne 0){throw 'Temporary client-metadata build failed'}
 $source=Join-Path $root 'app/build/generated/source/buildConfig/debug/com/example/firststep/BuildConfig.java'
 $present=Get-Content -Raw -LiteralPath $source
 if($present -notmatch 'local-config-transition-fixture' -or $present -notmatch 'H07_CONFIGURED = false'){throw 'Client fixture not reflected or incorrectly reported Google-configured'}
 Copy-Item -LiteralPath $source -Destination (Join-Path $evidence 'config-present-BuildConfig.txt')
 # Only this script-created local fixture is removed; original user configuration is untouched.
 $resolved=[IO.Path]::GetFullPath($fixture)
 if(-not $resolved.StartsWith([IO.Path]::GetFullPath($evidence)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Temporary path escaped evidence directory'}
 Remove-Item -LiteralPath $resolved
 & .\gradlew.bat clean testDebugUnitTest assembleDebug assembleDebugAndroidTest "-Ph07ConfigPath=$fixture" --console=plain *> (Join-Path $evidence 'config-absent-clean-build.log')
 if($LASTEXITCODE -ne 0){throw 'Clean unconfigured build failed'}
 $absent=Get-Content -Raw -LiteralPath $source
 foreach($field in @('H07_APP_ID','H07_API_KEY','H07_PROJECT_ID','H07_WEB_CLIENT_ID')){if($absent -notmatch ($field+' = "";')){throw "Stale configuration remains: $field"}}
 if($absent -notmatch 'H07_CONFIGURED = false'){throw 'Unconfigured flag not false'}
 Copy-Item -LiteralPath $source -Destination (Join-Path $evidence 'config-absent-BuildConfig.txt')
 $stale=Get-ChildItem -LiteralPath (Join-Path $root 'app/build/generated') -Recurse -File -Filter '*.xml' | Select-String -Pattern 'google_app_id|default_web_client_id|local-config-transition-fixture'
 if($stale){throw 'Stale generated Firebase resource detected'}
 if($originalHash){if((Get-FileHash -LiteralPath $original).Hash -ne $originalHash){throw 'Original user config changed'}}elseif(Test-Path -LiteralPath $original){Write-Host 'A user config arrived during checks; it was not read by the isolated build or modified.'}
 Write-Host 'PASS: temporary client metadata -> removed fixture -> clean absent build; all H07 values empty; no stale generated XML.'
 Write-Host 'No OAuth ID fabricated. This does not certify a real Google-configured build/login. Original user configuration preserved.'
}finally{Pop-Location}
