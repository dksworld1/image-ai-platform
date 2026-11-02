# 로컬 서버 설정: 필요한 값을 수정한 뒤 start-local.ps1을 실행하세요.
[int] $FrontendPort = 4173
[int] $BackendPort = 8080
[bool] $SkipFrontendBuild = $false
$ProjectJavaHome = 'C:\Program Files\Java\jdk-21'
