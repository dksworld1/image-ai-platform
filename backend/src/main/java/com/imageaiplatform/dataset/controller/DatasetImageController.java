package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.service.DatasetImageService;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/datasets/{datasetId}/images")
public class DatasetImageController {
    private final DatasetImageService imageService;

    public DatasetImageController(DatasetImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping
    public List<DatasetImageResponse> list(@PathVariable String datasetId) {
        return imageService.list(datasetId).stream().map(DatasetImageResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetImageResponse upload(@PathVariable String datasetId, @RequestPart("file") MultipartFile file) throws IOException {
        try {
            return DatasetImageResponse.from(imageService.upload(datasetId, file.getOriginalFilename(), file.getBytes()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 이미지입니다.");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
