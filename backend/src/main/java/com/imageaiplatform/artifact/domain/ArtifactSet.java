package com.imageaiplatform.artifact.domain;

import java.time.OffsetDateTime;

public record ArtifactSet(String setId, String workspaceId, String name, String purpose, String description, OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {}
