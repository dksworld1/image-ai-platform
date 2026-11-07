#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(dirname -- "$script_dir")"

# Windows 실행 스크립트와 동일한 포트 설정을 사용합니다.
port="$(sed -nE 's/^[[:space:]]*\[int\][[:space:]]*\$BackendPort[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "$script_dir/local.settings.ps1")"
if [[ ! "$port" =~ ^[0-9]+$ ]] || (( 10#$port < 1 || 10#$port > 65535 )); then
    echo 'local.settings.ps1의 BackendPort는 1~65535 사이의 정수여야 합니다.' >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo 'Java 21 이상을 설치하세요.' >&2
    exit 1
fi
java_version="$(java -version 2>&1)"
if [[ ! "$java_version" =~ version[[:space:]]+\"([0-9]+) ]] || (( BASH_REMATCH[1] < 21 )); then
    echo 'Java 21 이상이 필요합니다.' >&2
    exit 1
fi

maven_command="$(command -v mvn || true)"
if [[ ! -x "$maven_command" ]]; then
    echo 'Maven 3.6.3 이상을 설치하세요.' >&2
    exit 1
fi

cd -- "$repo_root/backend"
echo "백엔드 서버: http://localhost:$port (프로필: local, 중지: Ctrl+C)"
exec "$maven_command" spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.arguments=--server.port=$port"
