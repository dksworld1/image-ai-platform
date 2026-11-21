package com.imageaiplatform.artifact.domain;

import java.time.OffsetDateTime;

public record Artifact(String artifactId, String workspaceId, String dataType, String schemaName, String schemaVersion, String status, String description, OffsetDateTime retentionUntil, Boolean protectedFlag, OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {}
