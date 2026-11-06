package com.imageaiplatform.auth.domain;

/** auth_user_role의 복합 키에 대응하는 사용자 역할 할당입니다. */
public record AuthUserRole(String username, String roleCode) {}
