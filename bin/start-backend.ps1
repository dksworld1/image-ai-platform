$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

try {
    . (Join-Path $PSScriptRoot 'local.settings.ps1')
    $Port = $BackendPort
    if ($Port -lt 1 -or $Port -gt 65535) { throw 'BackendPort는 1~65535 사이의 값이어야 합니다.' }
    $projectJavaCommand = Join-Path $ProjectJavaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $projectJavaCommand)) {
        throw "프로젝트 전용 JDK 21을 찾을 수 없습니다: $ProjectJavaHome"
    }

    $repoRoot = Split-Path -Parent $PSScriptRoot
    $backendPath = Join-Path $repoRoot 'backend'

    $mavenCommand = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if (-not $mavenCommand) {
        $mavenHome = $env:MAVEN_HOME
        if (-not $mavenHome) {
            $mavenHome = [Environment]::GetEnvironmentVariable('MAVEN_HOME', 'Machine')
        }
        if ($mavenHome) {
            $candidate = Join-Path $mavenHome 'bin\mvn.cmd'
            if (Test-Path -LiteralPath $candidate) { $mavenCommand = $candidate }
        }
    }
    if (-not $mavenCommand) {
        throw 'Maven을 찾을 수 없습니다. Maven 3.6.3 이상을 설치한 뒤 다시 실행하세요.'
    }
    Push-Location $backendPath
    $previousServerPort = $env:SERVER_PORT
    $previousJavaHome = $env:JAVA_HOME
    $previousPath = $env:PATH
    try {
        $env:JAVA_HOME = $ProjectJavaHome
        $env:PATH = (Join-Path $ProjectJavaHome 'bin') + ';' + $previousPath
        $env:SERVER_PORT = [string] $Port
        Write-Host "백엔드 서버: http://localhost:$Port (Java: $env:JAVA_HOME, 중지하려면 Ctrl+C)"
        & $mavenCommand spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw "백엔드 서버 종료 코드: $LASTEXITCODE." }
    }
    finally {
        if ($null -eq $previousServerPort) {
            Remove-Item Env:SERVER_PORT -ErrorAction SilentlyContinue
        }
        else {
            $env:SERVER_PORT = $previousServerPort
        }
        if ($null -eq $previousJavaHome) {
            Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        }
        else {
            $env:JAVA_HOME = $previousJavaHome
        }
        $env:PATH = $previousPath
        Pop-Location
    }

    Write-Host '백엔드 서버가 종료되었습니다.' -ForegroundColor Yellow
}
catch {
    Write-Host "오류: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    [void](Read-Host '작업이 끝났습니다. 이 창을 닫으려면 Enter를 누르세요')
}
