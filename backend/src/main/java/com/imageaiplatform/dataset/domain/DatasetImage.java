package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetImage(String imageId, String datasetId, String originalFilename, String storagePath,
        String mediaType, Long sizeBytes, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        String createdBy, String updatedBy, String thumbnailPath, Boolean deleted, Integer versionCount,
        String previousImageId, String creatorUserId, String creatorUsername, String updaterUserId, String updaterUsername) {
    public DatasetImage withDeletion(OffsetDateTime time, String actor) {
        return new DatasetImage(imageId, datasetId, originalFilename, storagePath, mediaType, sizeBytes,
                createdAt, time, createdBy, actor, thumbnailPath, Boolean.TRUE, versionCount, previousImageId,
                creatorUserId, creatorUsername, null, null);
    }
}
