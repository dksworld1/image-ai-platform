package com.imageaiplatform.auth.domain;

/** auth_role_permission의 복합 키에 대응하는 역할 권한 할당입니다. */
public record AuthRolePermission(String roleCode, String permissionCode) {}
