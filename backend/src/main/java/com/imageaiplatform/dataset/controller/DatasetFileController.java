package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.service.DatasetFileService;
import com.imageaiplatform.dataset.domain.FileDescriptionChange;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
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
@RequestMapping("/api/datasets/{datasetId}/files")
public class DatasetFileController {
    private final DatasetFileService fileService;

    public DatasetFileController(DatasetFileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public List<DatasetFileResponse> list(@PathVariable String datasetId) {
        return fileService.list(datasetId).stream().map(DatasetFileResponse::from).toList();
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String datasetId, @PathVariable String fileId) {
        try {
            var content = fileService.content(datasetId, fileId);
            String filename = content.originalFilename().substring(content.originalFilename().lastIndexOf('/') + 1);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mediaType()))
                    .cacheControl(CacheControl.noStore())
                    .header("Content-Disposition", ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                    .body(content.contents());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File is unavailable.");
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetFileResponse upload(@PathVariable String datasetId, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") Boolean replaceExisting, @RequestParam(required = false) String description, @RequestParam(required = false) String relativePath) throws IOException {
        try {
            return DatasetFileResponse.from(Boolean.TRUE.equals(replaceExisting)
                    ? fileService.replace(datasetId, file.getOriginalFilename(), file.getBytes(), description, relativePath)
                    : fileService.upload(datasetId, file.getOriginalFilename(), file.getBytes(), description, relativePath));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 데이터 파일입니다.");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PatchMapping("/descriptions")
    public List<DatasetFileResponse> updateDescriptions(@PathVariable String datasetId, @Valid @RequestBody UpdateFileDescriptionsRequest request) {
        try {
            return fileService.updateDescriptions(datasetId, request.items().stream()
                    .map(item -> new FileDescriptionChange(item.fileId(), item.description())).toList())
                    .stream().map(DatasetFileResponse::from).toList();
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File is unavailable.");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String datasetId, @PathVariable String fileId) {
        try {
            fileService.delete(datasetId, fileId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File is unavailable.");
        }
    }
}
