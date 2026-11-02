param([string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw 'Android Studio JDK 경로를 확인하세요.' }
$env:JAVA_HOME = $JavaHome
Push-Location -LiteralPath $projectRoot
try {
    & .\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw '빌드 또는 검증 실패. 위 오류를 수정한 뒤 다시 실행하세요.' }
} finally { Pop-Location }
