package com.imageaiplatform.dataset.dao;

import com.imageaiplatform.dataset.domain.DatasetVersion;
import com.imageaiplatform.dataset.domain.DatasetVersionDraft;
import com.imageaiplatform.dataset.domain.DatasetVersionEntry;
import com.imageaiplatform.dataset.domain.DatasetVersionLocation;
import java.time.OffsetDateTime;
import java.util.List;

public interface DatasetVersionDao {
    DatasetVersionDraft findDraft(String datasetId);
    DatasetVersionDraft findDraft(String datasetId, String draftId);
    List<DatasetVersionDraft> listDrafts(String datasetId);
    String lockWorkingDataset(String datasetId);
    void insertWorkingDataset(DatasetVersionDraft draft, DatasetVersion base);
    void copyDraftImage(DatasetVersionDraft draft, DatasetVersionEntry entry, String id);
    void copyDraftFile(DatasetVersionDraft draft, DatasetVersionEntry entry, String id);
    void insertDraft(DatasetVersionDraft draft);
    void updateDraft(DatasetVersionDraft draft);
    void deleteDraft(String datasetId);
    void insert(DatasetVersion version);
    Integer nextNumber(String datasetId);
    Boolean exists(String datasetId);
    List<DatasetVersion> list(String datasetId);
    DatasetVersion find(String datasetId, String versionId);
    DatasetVersion findDraftBase(String datasetId, String versionId);
    void markVersionDeleted(String datasetId, String versionId, OffsetDateTime now, String actor);
    void snapshotImages(String datasetId, String versionId, OffsetDateTime now, String actor);
    void snapshotFiles(String datasetId, String versionId, OffsetDateTime now, String actor);
    List<DatasetVersionEntry> entries(String versionId);
    Integer restoreImage(String datasetId, DatasetVersionEntry entry, OffsetDateTime now, String actor);
    Integer restoreFile(String datasetId, DatasetVersionEntry entry, OffsetDateTime now, String actor);
    Integer restoreMetadata(DatasetVersion version, OffsetDateTime now, String actor);
    DatasetVersionLocation imageLocation(String versionId, String entryId);
    DatasetVersionLocation fileLocation(String versionId, String entryId);
}
