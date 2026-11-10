package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.domain.Dataset;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetResponse create(@Valid @RequestBody CreateDatasetRequest request) {
        try {
            Dataset dataset = datasetService.create(request.name(), request.description());
            return new DatasetResponse(dataset.datasetId(), dataset.name(), dataset.description(),
                    dataset.createdAt(), dataset.updatedAt(), dataset.createdBy(), dataset.updatedBy());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
