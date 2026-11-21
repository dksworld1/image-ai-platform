package com.imageaiplatform.artifact.domain;

import java.time.OffsetDateTime;

public record ArtifactSetVersion(String versionId, String setId, Integer versionNumber, String status, Long itemCount, String compositionChecksum, OffsetDateTime confirmedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {}
