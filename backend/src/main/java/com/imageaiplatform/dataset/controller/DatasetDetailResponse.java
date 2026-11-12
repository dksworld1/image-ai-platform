package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.domain.DatasetListItem;
import java.time.OffsetDateTime;

public record DatasetDetailResponse(String datasetId, String name, String description,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, String workspaceId, String workspaceName,
        String creatorUserId, String creatorUsername) {
    public static DatasetDetailResponse from(DatasetListItem dataset) {
        return new DatasetDetailResponse(dataset.datasetId(), dataset.name(), dataset.description(),
                dataset.createdAt(), dataset.updatedAt(), dataset.workspaceId(), dataset.workspaceName(),
                dataset.creatorUserId(), dataset.creatorUsername());
    }
}
