package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.service.DatasetImageService;
import com.imageaiplatform.dataset.domain.ImageDescriptionChange;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping(value = "/{imageId}/thumbnail", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> thumbnail(@PathVariable String datasetId, @PathVariable String imageId) {
        try {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                    .body(imageService.thumbnail(datasetId, imageId));
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thumbnail is unavailable.");
        }
    }

    @GetMapping("/{imageId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String datasetId, @PathVariable String imageId) {
        try {
            var content = imageService.content(datasetId, imageId);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mediaType()))
                    .cacheControl(CacheControl.noStore()).body(content.contents());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image is unavailable.");
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetImageResponse upload(@PathVariable String datasetId, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") Boolean replaceExisting, @RequestParam(required = false) String description, @RequestParam(required = false) String relativePath) throws IOException {
        try {
            return DatasetImageResponse.from(Boolean.TRUE.equals(replaceExisting)
                    ? imageService.replace(datasetId, file.getOriginalFilename(), file.getBytes(), description, relativePath)
                    : imageService.upload(datasetId, file.getOriginalFilename(), file.getBytes(), description, relativePath));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 이미지입니다.");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PatchMapping("/descriptions")
    public List<DatasetImageResponse> updateDescriptions(@PathVariable String datasetId, @Valid @RequestBody UpdateImageDescriptionsRequest request) {
        try {
            return imageService.updateDescriptions(datasetId, request.items().stream()
                    .map(item -> new ImageDescriptionChange(item.imageId(), item.description())).toList())
                    .stream().map(DatasetImageResponse::from).toList();
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image is unavailable.");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String datasetId, @PathVariable String imageId) {
        try {
            imageService.delete(datasetId, imageId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image is unavailable.");
        }
    }
}
