package com.imageaiplatform.dataset.service;

import com.imageaiplatform.dataset.dao.DatasetDao;
import com.imageaiplatform.dataset.dao.DatasetImageDao;
import com.imageaiplatform.dataset.domain.DatasetImageHistory;
import com.imageaiplatform.dataset.dao.DatasetFileDao;
import com.imageaiplatform.dataset.domain.DatasetFileHistory;
import com.imageaiplatform.dataset.domain.Dataset;
import com.imageaiplatform.dataset.domain.DatasetListItem;
import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetService {
    private final DatasetDao datasetDao;
    private final DatasetImageDao imageDao;
    private final DatasetFileDao fileDao;

    public DatasetService(DatasetDao datasetDao, DatasetImageDao imageDao, DatasetFileDao fileDao) {
        this.datasetDao = datasetDao;
        this.imageDao = imageDao;
        this.fileDao = fileDao;
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:item:create') and @workspaceService.isMemberOfAll(#workspaceIds)")
    @Transactional("appTransactionManager")
    public List<Dataset> create(String name, String description, List<String> workspaceIds) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var datasets = new ArrayList<Dataset>();
        for (String workspaceId : workspaceIds.stream().distinct().toList()) {
            var dataset = new Dataset(UUID.randomUUID().toString(), name, description, now, now, actor, actor, workspaceId);
            datasetDao.insert(dataset);
            datasets.add(dataset);
        }
        return List.copyOf(datasets);
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:item:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<DatasetListItem> list() {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        return datasetDao.findVisibleToUser(actor);
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:item:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public DatasetListItem detail(String datasetId) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        var dataset = datasetDao.findVisibleById(datasetId, actor);
        if (dataset == null) {
            throw new AccessDeniedException("Dataset is unavailable or active workspace membership is required.");
        }
        return dataset;
    }

    public boolean canDelete(String datasetId) {
        return canManage(datasetId);
    }

    public boolean canManage(String datasetId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && Boolean.TRUE.equals(datasetDao.canDelete(datasetId, authentication.getName()));
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:item:delete') and @datasetService.canDelete(#datasetId)")
    @Transactional("appTransactionManager")
    public void delete(String datasetId) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        if (datasetDao.lockForDeletion(datasetId, actor) == null) {
            throw new AccessDeniedException("Dataset is unavailable or deletion is not allowed.");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        for (var image : imageDao.findByDatasetId(datasetId)) {
            if (!Integer.valueOf(1).equals(imageDao.insertHistory(new DatasetImageHistory(UUID.randomUUID().toString(),
                    "DELETE", image.withDeletion(now, actor), now, actor)))) {
                throw new IllegalStateException("Could not record image deletion history.");
            }
        }
        for (var file : fileDao.findByDatasetId(datasetId)) {
            if (!Integer.valueOf(1).equals(fileDao.insertHistory(new DatasetFileHistory(UUID.randomUUID().toString(),
                    "DELETE", file.withDeletion(now, actor), now, actor)))) {
                throw new IllegalStateException("Could not record data file deletion history.");
            }
        }
        if (!Integer.valueOf(1).equals(datasetDao.deleteAllowed(datasetId, actor))) {
            throw new AccessDeniedException("Dataset is unavailable or deletion is not allowed.");
        }
    }
}
