package com.imageaiplatform.dataset.dao;

import com.imageaiplatform.dataset.domain.Dataset;
import com.imageaiplatform.dataset.domain.DatasetListItem;
import java.util.List;

public interface DatasetDao {
    void insert(Dataset dataset);
    List<DatasetListItem> findVisibleToUser(String userKey);
    DatasetListItem findVisibleById(String datasetId, String userKey);
    Boolean canDelete(String datasetId, String userKey);
    Integer deleteAllowed(String datasetId, String userKey);
    String lockForDeletion(String datasetId, String userKey);
}
