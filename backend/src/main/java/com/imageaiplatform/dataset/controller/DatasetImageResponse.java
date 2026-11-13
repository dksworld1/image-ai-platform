package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.domain.DatasetImage;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

public record DatasetImageResponse(String imageId, String datasetId, String originalFilename,
        String mediaType, Long sizeBytes, OffsetDateTime createdAt, String createdBy, String thumbnailUrl,
        OffsetDateTime updatedAt, String updatedBy, Integer versionCount,
        String creatorUserId, String creatorUsername, String updaterUserId, String updaterUsername) {
    public static DatasetImageResponse from(DatasetImage image) {
        return new DatasetImageResponse(image.imageId(), image.datasetId(), image.originalFilename(),
                image.mediaType(), image.sizeBytes(), image.createdAt(), image.createdBy(), image.thumbnailPath() == null ? null
                    : "/api/datasets/" + UriUtils.encodePathSegment(image.datasetId(), StandardCharsets.UTF_8)
                        + "/images/" + UriUtils.encodePathSegment(image.imageId(), StandardCharsets.UTF_8) + "/thumbnail",
                image.updatedAt(), image.updatedBy(), image.versionCount(), image.creatorUserId(), image.creatorUsername(),
                image.updaterUserId(), image.updaterUsername());
    }
}
