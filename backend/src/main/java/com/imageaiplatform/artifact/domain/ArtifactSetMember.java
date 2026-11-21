package com.imageaiplatform.artifact.domain;

import java.time.OffsetDateTime;

public record ArtifactSetMember(String versionId, String artifactId, Long ordinal, String sourceKey, OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {}
