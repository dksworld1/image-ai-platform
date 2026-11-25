package com.imageaiplatform.dataset.domain;

import java.time.OffsetDateTime;

public record DatasetVersionDraft(String draftId, String datasetId, String workingDatasetId, String baseVersionId, String name, String changeNote,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                  String createdBy, String updatedBy, Long imageCount, Long fileCount,
                                  String creatorUserId, String creatorUsername, Integer baseVersionNumber) {}
