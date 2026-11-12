package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.domain.DatasetImage;
import java.time.OffsetDateTime;

public record DatasetImageResponse(String imageId, String datasetId, String originalFilename,
        String mediaType, Long sizeBytes, OffsetDateTime createdAt, String createdBy) {
    public static DatasetImageResponse from(DatasetImage image) {
        return new DatasetImageResponse(image.imageId(), image.datasetId(), image.originalFilename(),
                image.mediaType(), image.sizeBytes(), image.createdAt(), image.createdBy());
    }
}
