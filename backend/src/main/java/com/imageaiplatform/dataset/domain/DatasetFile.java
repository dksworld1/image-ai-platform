package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetFile(String fileId, String datasetId, String originalFilename, String storagePath,
        String mediaType, Long sizeBytes, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        String createdBy, String updatedBy, Boolean deleted, Integer versionCount,
        String previousFileId, String creatorUserId, String creatorUsername, String updaterUserId, String updaterUsername,
        OffsetDateTime firstRegisteredAt, String description) {
    public DatasetFile withDeletion(OffsetDateTime time, String actor) {
        return new DatasetFile(fileId, datasetId, originalFilename, storagePath, mediaType, sizeBytes,
                createdAt, time, createdBy, actor, Boolean.TRUE, versionCount, previousFileId,
                creatorUserId, creatorUsername, null, null, firstRegisteredAt, description);
    }

    public DatasetFile withDescription(String value, OffsetDateTime time, String actor) {
        return new DatasetFile(fileId, datasetId, originalFilename, storagePath, mediaType, sizeBytes,
                createdAt, time, createdBy, actor, deleted, versionCount, previousFileId,
                creatorUserId, creatorUsername, null, null, firstRegisteredAt, value);
    }
}
