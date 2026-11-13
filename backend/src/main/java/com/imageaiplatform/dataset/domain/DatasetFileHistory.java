package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetFileHistory(String historyId, String action, DatasetFile entry,
        OffsetDateTime createdAt, String createdBy) {}
