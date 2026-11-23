package com.imageaiplatform.dataset.dao;

import com.imageaiplatform.dataset.domain.DatasetVersion;
import com.imageaiplatform.dataset.domain.DatasetVersionEntry;
import com.imageaiplatform.dataset.domain.DatasetVersionLocation;
import java.time.OffsetDateTime;
import java.util.List;

public interface DatasetVersionDao {
    void insert(DatasetVersion version);
    Integer nextNumber(String datasetId);
    Boolean exists(String datasetId);
    List<DatasetVersion> list(String datasetId);
    DatasetVersion find(String datasetId, String versionId);
    void snapshotImages(String datasetId, String versionId, OffsetDateTime now, String actor);
    void snapshotFiles(String datasetId, String versionId, OffsetDateTime now, String actor);
    List<DatasetVersionEntry> entries(String versionId);
    Integer restoreImage(String datasetId, DatasetVersionEntry entry, OffsetDateTime now, String actor);
    Integer restoreFile(String datasetId, DatasetVersionEntry entry, OffsetDateTime now, String actor);
    Integer restoreMetadata(DatasetVersion version, OffsetDateTime now, String actor);
    DatasetVersionLocation imageLocation(String versionId, String entryId);
    DatasetVersionLocation fileLocation(String versionId, String entryId);
}
