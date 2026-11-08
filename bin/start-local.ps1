$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

try {
    $frontendScript = Join-Path $PSScriptRoot 'start-local-frontend.ps1'
    $backendScript = Join-Path $PSScriptRoot 'start-local-backend.ps1'
    $settingsScript = Join-Path $PSScriptRoot 'local.settings.ps1'
    $powerShell = Join-Path $PSHOME 'powershell.exe'

    if (-not (Test-Path $frontendScript)) { throw "프런트엔드 시작 스크립트를 찾을 수 없습니다: $frontendScript" }
    if (-not (Test-Path $backendScript)) { throw "백엔드 시작 스크립트를 찾을 수 없습니다: $backendScript" }
    if (-not (Test-Path $settingsScript)) { throw "로컬 설정 파일을 찾을 수 없습니다: $settingsScript" }
    . $settingsScript

    if (-not (Test-Path $powerShell)) {
        $powerShellCommand = Get-Command powershell.exe -ErrorAction SilentlyContinue
        if (-not $powerShellCommand) { throw 'Windows PowerShell(powershell.exe)을 찾을 수 없습니다.' }
        $powerShell = $powerShellCommand.Source
    }

    $frontendArguments = @(
        '-NoExit',
        '-ExecutionPolicy', 'Bypass',
        '-File', "`"$frontendScript`""
    )
    $backendArguments = @(
        '-NoExit',
        '-ExecutionPolicy', 'Bypass',
        '-File', "`"$backendScript`""
    )

    $frontendProcess = Start-Process -FilePath $powerShell -ArgumentList $frontendArguments -PassThru
    $backendProcess = Start-Process -FilePath $powerShell -ArgumentList $backendArguments -PassThru

    Write-Host '프런트엔드와 백엔드를 별도의 PowerShell 창에서 시작했습니다.'
    Write-Host "프런트엔드: http://localhost:$FrontendPort (PID $($frontendProcess.Id))"
    Write-Host "백엔드: http://localhost:$BackendPort (PID $($backendProcess.Id))"
    Write-Host '서버 창은 유지됩니다. 이 실행 창은 Enter를 누르면 닫힙니다.'
}
catch {
    Write-Host "오류: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    [void](Read-Host '작업이 끝났습니다. 이 창을 닫으려면 Enter를 누르세요')
}
