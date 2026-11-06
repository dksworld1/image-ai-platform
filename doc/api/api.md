# API 계약

상태: 초기 계약  
갱신일: 2026-11-06
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

## API 접근 정책

- 공개 API: `GET /api/health`, `POST /api/auth/token`. 인증이 필수는 아니지만 잘못된 Bearer 토큰을 보내면 기존 JWT 필터가 401로 거부합니다.
- 컨트롤러의 `@PublicApi`는 공개, `@RequirePermission`은 활성 계정 인증만 필요, `@RequirePermission("도메인:리소스:동작")`은 인증과 지정 권한이 필요함을 뜻합니다.
- 비공개 요청에서 인증 없음·잘못된/만료된 토큰·계정 없음·비활성은 401입니다. 인증 후 세부 권한 부족은 403입니다. 실제 업무의 리소스 소유권·공유 범위는 별도 검사 대상입니다.
- 인터셉터가 거부하면 `{"error":"UNAUTHORIZED"}` 또는 `{"error":"FORBIDDEN"}`을 반환합니다. 권한 조회 DB 장애는 500과 `{"error":"INTERNAL_SERVER_ERROR"}`로 처리합니다. 기존 로그인·토큰 검증 실패 응답은 기존 Spring 오류 처리를 유지합니다.
- 정책 없는 MVC 핸들러와 정적 리소스 핸들러는 기본 거부합니다. 직접 `/error` 요청도 기본 거부하며 서버 내부의 정상 ERROR 디스패치만 오류 응답을 렌더링합니다.
- 정상 CORS preflight는 기존 Spring CORS 처리에서 종료되고 실제 요청에 위 인증·인가를 적용합니다. API 인가 테스트용 경로는 테스트 소스에만 있으며 서비스 API로 제공하지 않습니다.

## 변경 원칙

- 새 API는 메서드, 경로, 요청/응답 예시, 오류 응답, 인증 요구 여부를 이 문서에 기록합니다.
- 호환성에 영향을 주는 변경은 버전 정책과 마이그레이션 계획을 먼저 설계합니다.
- 구현의 실제 계약이 문서와 다르면 둘 중 하나를 변경해 일치시킵니다.
