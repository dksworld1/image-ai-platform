# ADR-037: DB 외 일반 설정의 application.yaml 직접 관리

- 상태: 채택
- 날짜: 2026-11-12
- 대체/관련 기록: [ADR-013](ADR-013-topic-based-spring-yaml.md)의 일반 설정 주제별 YAML import 방침 대체. DB 접속 YAML 분리와 외부 비밀정보 정책은 유지

## 맥락

사용자는 DB를 제외한 대부분의 설정을 application.yaml에 직접 작성하고 import로 분리하지 않도록 지시했습니다. 설정을 확인하기 위해 여러 주제별 파일을 따라가야 하는 구조를 줄입니다.

## 대안

- 모든 일반 설정을 주제별 YAML로 import: 기존 방침이나 현재 사용자 지시와 다릅니다.
- DB까지 한 파일에 병합: DB 예외·외부 접속 정보 관리 방침과 다릅니다.
- 일반 설정은 application.yaml, DB는 기존 분리 유지: 사용자 지시를 따르면서 프로파일과 비밀정보 관리 경계를 유지합니다.

## 결정

DB 외 일반 기본값은 application.yaml에 직접 작성합니다. 환경별 차이만 application-{profile}.yaml에서 재정의합니다. DB 접속 설정은 config/datasource의 별도 YAML과 기존 spring.config.import를 유지합니다. 비밀값은 저장소 밖에서 관리하고 spring.config.additional-location으로 지정합니다. 기존 Logback XML·MyBatis XML과 Spring 이전 런타임 properties의 역할은 변경하지 않습니다.

이번 변경에서 이미지 저장 루트·최대 파일 용량·multipart 제한을 application.yaml로 옮기고 config/storage/images.yaml과 그 import를 제거했습니다. 앞으로 새로운 일반 설정에도 같은 방침을 적용합니다.

## 결과

설정 키·기본값과 파일당 크기 제한의 단일 참조는 유지합니다. DB URL·비밀값·프로파일 동작은 변경하지 않습니다. 실제 서버 재시작·DB 적용은 이번 변경에서 수행하지 않습니다.
