package com.imageaiplatform.dataset.dao;

import com.imageaiplatform.dataset.domain.DatasetImage;
import java.util.List;

public interface DatasetImageDao {
    Boolean existsByOriginalFilename(String datasetId, String originalFilename);
    Integer insert(DatasetImage image);
    List<DatasetImage> findByDatasetId(String datasetId);
}
