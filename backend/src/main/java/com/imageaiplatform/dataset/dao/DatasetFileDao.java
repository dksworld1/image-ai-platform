package com.imageaiplatform.dataset.dao;

import com.imageaiplatform.dataset.domain.DatasetFile;
import com.imageaiplatform.dataset.domain.DatasetFileHistory;
import com.imageaiplatform.dataset.domain.DatasetFileTarget;
import java.util.List;

public interface DatasetFileDao {
    Integer insert(DatasetFile entry);
    List<DatasetFile> findByDatasetId(String datasetId);
    DatasetFile findById(String datasetId, String fileId);
    String lockDataset(String datasetId, String userKey);
    DatasetFile findActiveByFilename(String datasetId, String originalFilename);
    Integer markDeleted(DatasetFile entry);
    Integer updateDescription(DatasetFile entry);
    Integer insertHistory(DatasetFileHistory history);
    Boolean canRead(String datasetId, String userKey);
    Boolean canManage(String datasetId, String userKey);
    List<DatasetFileTarget> findTargets(String userKey);
}
