package com.imageaiplatform.auth.domain;

import java.time.OffsetDateTime;

public record AuthUser(String userKey, String userId, String username, String passwordHash, Boolean enabled,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {}
