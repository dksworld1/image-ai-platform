package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.domain.DatasetFile;
import java.time.OffsetDateTime;

public record DatasetFileResponse(String fileId, String datasetId, String originalFilename, String description,
        String mediaType, Long sizeBytes, OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy,
        Integer versionCount, OffsetDateTime firstRegisteredAt, String creatorUserId, String creatorUsername,
        String updaterUserId, String updaterUsername) {
    public static DatasetFileResponse from(DatasetFile file) {
        return new DatasetFileResponse(file.fileId(), file.datasetId(), file.originalFilename(), file.description(),
                file.mediaType(), file.sizeBytes(), file.createdAt(), file.updatedAt(), file.createdBy(), file.updatedBy(),
                file.versionCount(), file.firstRegisteredAt(), file.creatorUserId(), file.creatorUsername(), file.updaterUserId(), file.updaterUsername());
    }
}
