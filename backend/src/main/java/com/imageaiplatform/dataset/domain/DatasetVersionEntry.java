package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetVersionEntry(String kind, String entryId, String originalFilename, String description, String mediaType, Long sizeBytes, Integer versionCount, OffsetDateTime sourceCreatedAt, String sourceCreatedBy) {}
