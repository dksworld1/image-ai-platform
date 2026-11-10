package com.imageaiplatform.dataset.service;

import com.imageaiplatform.dataset.dao.DatasetDao;
import com.imageaiplatform.dataset.domain.Dataset;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetService {
    private final DatasetDao datasetDao;

    public DatasetService(DatasetDao datasetDao) {
        this.datasetDao = datasetDao;
    }

    @PreAuthorize("@authorizationService.isActiveAccount()")
    @Transactional("appTransactionManager")
    public Dataset create(String name, String description) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        Dataset dataset = new Dataset(UUID.randomUUID().toString(), name, description, now, now, actor, actor);
        datasetDao.insert(dataset);
        return dataset;
    }
}
