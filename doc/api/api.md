# API 계약

상태: 초기 계약  
갱신일: 2026-11-01  
기본 URL: `http://localhost:8080`

## 헬스체크

### `GET /api/health`

API 프로세스가 요청을 처리할 수 있는지 확인합니다.

성공 응답 `200 OK`:

```json
{ "status": "UP" }
```

이 엔드포인트는 인프라 및 외부 의존성의 상태까지 보장하지 않습니다.

## 인증

### `POST /api/auth/token`

아이디와 비밀번호를 검증하고 서명된 JWT access token을 발급합니다. 비밀번호는 서버에 저장된 BCrypt 해시와 비교합니다.

요청:

```json
{ "username": "user-id", "password": "user-password" }
```

성공 응답 `200 OK`:

```json
{ "accessToken": "<JWT>", "tokenType": "Bearer", "expiresIn": 900 }
```

존재하지 않거나 비활성화된 계정, 잘못된 비밀번호는 동일한 `401 Unauthorized` 응답을 반환합니다. 보호된 API에는 `Authorization: Bearer <JWT>` 헤더를 전달합니다. 토큰은 기본 15분 후 만료되며 refresh token은 발급하지 않습니다. 현재 사용자 생성·비밀번호 변경 API는 제공하지 않습니다.

`local`과 `test` 프로파일은 각 YAML의 비운영 전용 JWT 서명 키를 사용합니다. `dev`와 `prod`는 `JWT_SECRET` 환경 변수로 UTF-8 기준 32바이트 이상의 키를 주입해야 합니다. `app.auth_user` 테이블은 Flyway로 생성하며, 계정 행은 자동 생성하지 않습니다. 테이블 정의는 [인증 테이블 정의서](../db-schema/tables/auth/auth-user.md)에 있습니다.

## 변경 원칙

- 새 API는 메서드, 경로, 요청/응답 예시, 오류 응답, 인증 요구 여부를 이 문서에 기록합니다.
- 호환성에 영향을 주는 변경은 버전 정책과 마이그레이션 계획을 먼저 설계합니다.
- 구현의 실제 계약이 문서와 다르면 둘 중 하나를 변경해 일치시킵니다.
