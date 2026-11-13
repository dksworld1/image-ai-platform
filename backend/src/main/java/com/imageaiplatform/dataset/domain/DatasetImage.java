package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetImage(String imageId, String datasetId, String originalFilename, String storagePath,
        String mediaType, Long sizeBytes, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        String createdBy, String updatedBy, String thumbnailPath, Boolean deleted, Integer versionCount,
        String previousImageId, String creatorUserId, String creatorUsername, String updaterUserId, String updaterUsername,
        OffsetDateTime firstRegisteredAt, String description) {
    public DatasetImage withDeletion(OffsetDateTime time, String actor) {
        return new DatasetImage(imageId, datasetId, originalFilename, storagePath, mediaType, sizeBytes,
                createdAt, time, createdBy, actor, thumbnailPath, Boolean.TRUE, versionCount, previousImageId,
                creatorUserId, creatorUsername, null, null, firstRegisteredAt, description);
    }

    public DatasetImage withDescription(String value, OffsetDateTime time, String actor) {
        return new DatasetImage(imageId, datasetId, originalFilename, storagePath, mediaType, sizeBytes,
                createdAt, time, createdBy, actor, thumbnailPath, deleted, versionCount, previousImageId,
                creatorUserId, creatorUsername, null, null, firstRegisteredAt, value);
    }
}
