package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.domain.DatasetListItem;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.imageaiplatform.dataset.service.DatasetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public List<DatasetListItem> list() {
        return datasetService.list();
    }

    @DeleteMapping("/{datasetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String datasetId) {
        datasetService.delete(datasetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<DatasetResponse> create(@Valid @RequestBody CreateDatasetRequest request) {
        try {
            return datasetService.create(request.name(), request.description(), request.workspaceIds()).stream()
                    .map(dataset -> new DatasetResponse(dataset.datasetId(), dataset.name(), dataset.description(),
                            dataset.createdAt(), dataset.updatedAt(), dataset.createdBy(), dataset.updatedBy(), dataset.workspaceId())).toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
