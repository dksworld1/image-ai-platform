package com.imageaiplatform.auth.domain;

import java.time.OffsetDateTime;

/** auth_user_role의 복합 키에 대응하는 사용자 역할 할당입니다. */
public record AuthUserRole(String userKey, String roleCode,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {}
