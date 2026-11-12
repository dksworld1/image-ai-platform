package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetImage(String imageId, String datasetId, String originalFilename, String storagePath,
        String mediaType, Long sizeBytes, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        String createdBy, String updatedBy) {}
