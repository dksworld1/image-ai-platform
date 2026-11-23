package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetVersion(String versionId, String datasetId, Integer versionNumber, String name, String description, String changeNote, Long imageCount, Long fileCount, OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy, String creatorUserId, String creatorUsername) {}
