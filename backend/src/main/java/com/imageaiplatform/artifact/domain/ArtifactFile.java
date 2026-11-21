package com.imageaiplatform.artifact.domain;

import java.time.OffsetDateTime;

public record ArtifactFile(String fileId, String artifactId, String internalName, String rootKey, String relativePath, String mediaType, Long sizeBytes, String checksumAlgorithm, String checksum, OffsetDateTime deletedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {}
