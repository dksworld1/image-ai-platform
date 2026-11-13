package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetListItem(String datasetId, String name, String description,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, String workspaceId, String workspaceName,
        String creatorUserId, String creatorUsername, Long imageCount) {}
