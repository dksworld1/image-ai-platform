package com.imageaiplatform.auth.domain;

import java.time.OffsetDateTime;

/** auth_permission에 대응하는 부여 가능 권한. 코드는 도메인:리소스:동작 형식입니다. */
public record AuthPermission(String permissionCode, String displayName, String description,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {}
