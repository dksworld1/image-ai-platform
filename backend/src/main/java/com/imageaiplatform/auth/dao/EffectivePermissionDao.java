package com.imageaiplatform.auth.dao;

import java.util.Set;

/** 사용자 역할에 연결된 권한을 조회하는 전용 저장소 계약입니다. */
public interface EffectivePermissionDao {
    /**
     * 사용자에게 할당된 모든 역할의 권한 코드를 중복 없이 반환합니다.
     *
     * <p>입력은 null이나 공백이 아닌 서버에서 확인한 사용자명입니다. 부여 권한이 없으면
     * null 대신 빈 집합을 반환합니다. 조회 실패는 예외로 전달하며 빈 집합으로 대체하지 않습니다.
     * 반환 코드에는 와일드카드·역할 이름에 의한 암묵적 권한이나 공개 API 식별자를 포함하지 않습니다.
     *
     * <p>이 조회만으로 사용자 존재·활성 상태, 인증 여부, 리소스 접근 범위를 보장하지 않습니다.
     * 인가 서비스는 계정 상태를 별도로 확인해야 합니다.
     */
    Set<String> findEffectivePermissionCodesByUsername(String username);
}
