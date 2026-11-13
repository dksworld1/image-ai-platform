package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetImageHistory(String historyId, String action, DatasetImage image,
        OffsetDateTime createdAt, String createdBy) {}
