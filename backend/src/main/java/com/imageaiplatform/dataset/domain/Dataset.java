package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record Dataset(String datasetId, String name, String description,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy) {
    public Dataset {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("Dataset name must contain 1 to 200 characters.");
        }
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("Dataset description must not exceed 2000 characters.");
        }
        name = name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
