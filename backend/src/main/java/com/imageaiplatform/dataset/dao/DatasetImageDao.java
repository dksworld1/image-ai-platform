package com.imageaiplatform.dataset.dao;

import com.imageaiplatform.dataset.domain.DatasetImage;
import com.imageaiplatform.dataset.domain.DatasetImageHistory;
import com.imageaiplatform.dataset.domain.DatasetImageTarget;
import java.util.List;

public interface DatasetImageDao {
    Integer insert(DatasetImage image);
    List<DatasetImage> findByDatasetId(String datasetId);
    DatasetImage findById(String datasetId, String imageId);
    String lockDataset(String datasetId, String userKey);
    DatasetImage findActiveByFilename(String datasetId, String originalFilename);
    Integer markDeleted(DatasetImage image);
    Integer insertHistory(DatasetImageHistory history);
    Boolean canRead(String datasetId, String userKey);
    Boolean canManage(String datasetId, String userKey);
    List<DatasetImageTarget> findTargets(String userKey);
}
