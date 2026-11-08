$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

try {
    . (Join-Path $PSScriptRoot 'local.settings.ps1')
    $Port = $FrontendPort
    if ($Port -lt 1 -or $Port -gt 65535) { throw 'FrontendPort는 1~65535 사이의 값이어야 합니다.' }

    $repoRoot = Split-Path -Parent $PSScriptRoot
    $frontendPath = Join-Path $repoRoot 'frontend'

    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        throw 'npm을 찾을 수 없습니다. Node.js 22.12 이상을 설치한 뒤 다시 실행하세요.'
    }

    Push-Location $frontendPath
    try {
        if (-not (Test-Path (Join-Path $frontendPath 'node_modules'))) {
            Write-Host '프런트엔드 의존성을 설치합니다...'
            & npm install
            if ($LASTEXITCODE -ne 0) { throw "npm install 실패 (종료 코드: $LASTEXITCODE)." }
        }

        if (-not $SkipFrontendBuild) {
            Write-Host '프런트엔드를 빌드합니다...'
            & npm run build
            if ($LASTEXITCODE -ne 0) { throw "프런트엔드 빌드 실패 (종료 코드: $LASTEXITCODE)." }
        }

        Write-Host "프런트엔드 서버: http://localhost:$Port (중지하려면 Ctrl+C)"
        & npm run preview
        if ($LASTEXITCODE -ne 0) { throw "프런트엔드 서버 종료 코드: $LASTEXITCODE." }
    }
    finally {
        Pop-Location
    }

    Write-Host '프런트엔드 서버가 종료되었습니다.' -ForegroundColor Yellow
}
catch {
    Write-Host "오류: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    [void](Read-Host '작업이 끝났습니다. 이 창을 닫으려면 Enter를 누르세요')
}
