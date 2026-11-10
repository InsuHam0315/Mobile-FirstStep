param(
    [Parameter(Mandatory)][ValidatePattern('^emulator-[0-9]{4,5}$')][string]$Serial,
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$AndroidSdk = (Join-Path $env:LOCALAPPDATA 'Android/Sdk')
)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Use PowerShell 7 or newer.' }
if ($Serial -in 'emulator-5564', 'emulator-5572', 'emulator-5580') {
    throw 'Select the disposable progress SDK emulator.'
}
$adb = (Resolve-Path -LiteralPath (Join-Path $AndroidSdk 'platform-tools/adb.exe')).Path
$javaBin = (Resolve-Path -LiteralPath (Join-Path $JavaHome 'bin')).Path
$cli = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../node_modules/firebase-tools/lib/bin/firebase.js')).Path
$node = (Get-Command node.exe -CommandType Application).Source
$cache = (Resolve-Path -LiteralPath (Join-Path $env:USERPROFILE '.cache/firebase/emulators')).Path
$cliLib = Split-Path -Parent (Split-Path -Parent $cli)
$emulatorInfo = Get-Content -LiteralPath (Join-Path $cliLib 'emulator/downloadableEmulatorInfo.json') -Raw |
    ConvertFrom-Json
$jar = Join-Path $cache $emulatorInfo.firestore.downloadPathRelativeToCacheDir
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw 'Prepare the Firestore emulator cache before starting verification.'
}
$avdName = & $adb -H 127.0.0.1 -P 5037 -s $Serial emu avd name | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or $avdName -cnotmatch '^FirstStep_ProgressSdk_[A-Za-z0-9_]+$') {
    throw 'Use a running disposable AVD named FirstStep_ProgressSdk_<suffix>.'
}
$ports = 19383, 18383, 19283, 14683, 14783
if (Get-NetTCPConnection -State Listen -LocalPort $ports -ErrorAction SilentlyContinue) {
    throw 'A reserved verification port is already in use.'
}

$run = [Guid]::NewGuid().ToString('N')
$scratch = Join-Path $PSScriptRoot ".local/$run"
New-Item -ItemType Directory -Path $scratch | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../firestore.rules') -Destination $scratch
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'firebase.local.json') -Destination $scratch
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'run.mjs') -Destination $scratch
$rulesHash = (Get-FileHash -LiteralPath (Join-Path $scratch 'firestore.rules')).Hash
@('Project: demo-firststep-progress-sdk', "Rule SHA256: $rulesHash", "AVD: $avdName") |
    Set-Content -LiteralPath (Join-Path $scratch 'fixture.log')

$environment = @{
    JAVA_HOME = $JavaHome
    Path = "$javaBin;$(Split-Path -Parent $node);$env:SystemRoot\System32;$env:SystemRoot"
    SystemRoot = $env:SystemRoot
    WINDIR = $env:SystemRoot
    ComSpec = $env:ComSpec
    PATHEXT = '.COM;.EXE;.BAT;.CMD'
    TEMP = $env:TEMP
    TMP = $env:TMP
    XDG_CONFIG_HOME = (Join-Path $scratch 'config')
    CI = 'true'
    FIREBASE_CLI_DISABLE_MOTD = 'true'
    FIREBASE_EMULATORS_PATH = $cache
    FIRSTSTEP_PROGRESS_SDK_ADB = $adb
    FIRSTSTEP_PROGRESS_SDK_SERIAL = $Serial
    FIRSTSTEP_PROGRESS_SDK_RUN = $run
}
$launch = [Diagnostics.ProcessStartInfo]::new()
$launch.FileName = $node
$launch.WorkingDirectory = $scratch
$launch.UseShellExecute = $false
$launch.CreateNoWindow = $true
$launch.RedirectStandardOutput = $true
$launch.RedirectStandardError = $true
# The Firebase CLI writes child environment details to its debug log.
$launch.Environment.Clear()
foreach ($key in $environment.Keys) {
    if ($null -ne $environment[$key]) { $launch.Environment[$key] = $environment[$key] }
}
$argsForCli = @($cli, 'emulators:exec', '--only', 'auth,firestore', '--project',
    'demo-firststep-progress-sdk', '--config', 'firebase.local.json', 'node run.mjs')
foreach ($argument in $argsForCli) { $launch.ArgumentList.Add($argument) }
$process = [Diagnostics.Process]::Start($launch)
try {
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    @($stdout.GetAwaiter().GetResult(), $stderr.GetAwaiter().GetResult()) |
        Tee-Object -FilePath (Join-Path $scratch 'emulator.log')
    $result = $process.ExitCode
} finally {
    $process.Dispose()
}
Write-Host "Verification evidence: $scratch"
exit $result
