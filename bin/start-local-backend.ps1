$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

try {
    . (Join-Path $PSScriptRoot 'local.settings.ps1')
    $Port = $BackendPort
    if ($Port -lt 1 -or $Port -gt 65535) { throw 'BackendPort는 1~65535 사이의 값이어야 합니다.' }
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $backendPath = Join-Path $repoRoot 'backend'

    $mavenCommand = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if (-not $mavenCommand) {
        throw 'Maven을 찾을 수 없습니다. Maven 3.6.3 이상을 설치한 뒤 다시 실행하세요.'
    }
    $javaCommand = Join-Path $BackendJavaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $javaCommand)) {
        throw 'local.settings.ps1의 BackendJavaHome을 설치된 JDK 21 이상 경로로 지정하세요.'
    }
    $mavenHome = Split-Path -Parent (Split-Path -Parent $mavenCommand)
    $launcherJar = (Get-ChildItem -LiteralPath (Join-Path $mavenHome 'boot') -Filter 'plexus-classworlds-*.jar' | Select-Object -First 1).FullName
    $mavenArguments = @(
        '-classpath', $launcherJar,
        "-Dclassworlds.conf=$mavenHome\bin\m2.conf",
        "-Dmaven.home=$mavenHome",
        "-Dmaven.multiModuleProjectDirectory=$backendPath",
        'org.codehaus.plexus.classworlds.launcher.Launcher'
    )
    Write-Host "백엔드 Java: $javaCommand"
    Push-Location $backendPath
    try {
        & $javaCommand @mavenArguments spring-boot:run "-Dspring-boot.run.workingDirectory=$backendPath" "-Dspring-boot.run.arguments=--server.port=$Port"
        if ($LASTEXITCODE -ne 0) { throw "백엔드 서버 종료 코드: $LASTEXITCODE." }
    }
    finally {
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
