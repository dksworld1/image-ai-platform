package com.imageaiplatform.dataset.controller;

import java.time.OffsetDateTime;

public record DatasetResponse(String datasetId, String name, String description,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy, String workspaceId) {}
