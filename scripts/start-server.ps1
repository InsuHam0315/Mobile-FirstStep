param([int]$Port = 8787)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $projectRoot 'backend'
$localPath = Join-Path $projectRoot '.local'
$pythonPath = Join-Path $backendPath '.venv\Scripts\python.exe'
& python (Join-Path $PSScriptRoot 'provision_local_mysql.py')
if ($LASTEXITCODE -ne 0) { throw '첫발자국 전용 MySQL을 시작하지 못했습니다.' }
if (-not (Test-Path -LiteralPath $pythonPath)) {
    & python -m venv (Join-Path $backendPath '.venv')
    & $pythonPath -m pip install -r (Join-Path $backendPath 'requirements.txt')
    if ($LASTEXITCODE -ne 0) { throw '서버 라이브러리 설치에 실패했습니다.' }
}
try {
    $existingHealth = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2
    if ($existingHealth.status -eq 'ok' -and $existingHealth.database -eq 'mysql') {
        Write-Output "첫발자국 서버가 실행 중입니다. http://127.0.0.1:$Port/docs"
        exit 0
    }
} catch { }
$serverProcess = Start-Process -FilePath $pythonPath -ArgumentList "-m uvicorn app:app --host 127.0.0.1 --port $Port --no-access-log" -WorkingDirectory $backendPath -WindowStyle Hidden -RedirectStandardOutput (Join-Path $localPath 'api-stdout.log') -RedirectStandardError (Join-Path $localPath 'api-stderr.log') -PassThru
Set-Content -LiteralPath (Join-Path $localPath 'api.pid') -Value $serverProcess.Id -Encoding ascii
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 1
        if ($health.status -eq 'ok' -and $health.database -eq 'mysql') {
            Write-Output "첫발자국 서버 시작 완료. MySQL 127.0.0.1:3307 / API http://127.0.0.1:$Port/docs"
            exit 0
        }
    } catch { }
    Start-Sleep -Milliseconds 500
}
throw '서버 시작 실패. .local/api-stderr.log를 확인하세요.'
