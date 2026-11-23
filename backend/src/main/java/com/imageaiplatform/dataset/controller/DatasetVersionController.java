package com.imageaiplatform.dataset.controller;

import com.imageaiplatform.dataset.service.DatasetVersionService;
import com.imageaiplatform.dataset.domain.DatasetVersion;
import com.imageaiplatform.dataset.domain.DatasetVersionConflictException;
import com.imageaiplatform.dataset.domain.DatasetVersionEntry;
import com.imageaiplatform.dataset.domain.DatasetListItem;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasets")
public class DatasetVersionController {
    private final DatasetVersionService service;
    public DatasetVersionController(DatasetVersionService service) { this.service=service; }
    public record CreateRequest(String changeNote) {}
    @GetMapping("/version-targets") public List<DatasetListItem> targets() { return service.targets(); }
    @PostMapping("/{datasetId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetVersion create(@PathVariable String datasetId,@RequestBody CreateRequest request) { return service.create(datasetId,request.changeNote()); }
    @GetMapping("/{datasetId}/versions") public List<DatasetVersion> list(@PathVariable String datasetId) { return service.list(datasetId); }
    @GetMapping("/{datasetId}/versions/{versionId}")
    public DatasetVersion detail(@PathVariable String datasetId,@PathVariable String versionId) { return service.detail(datasetId,versionId); }
    @PostMapping("/{datasetId}/versions/{versionId}/restore")
    public DatasetVersion restore(@PathVariable String datasetId,@PathVariable String versionId) { return service.restore(datasetId,versionId); }
    @GetMapping("/{datasetId}/versions/{versionId}/entries")
    public List<DatasetVersionEntry> entries(@PathVariable String datasetId,@PathVariable String versionId) { return service.entries(datasetId,versionId); }
    @GetMapping("/{datasetId}/versions/{versionId}/{kind:images|files}/{entryId}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable String datasetId,@PathVariable String versionId,@PathVariable String kind,@PathVariable String entryId) {
        var content=service.content(datasetId,versionId,kind,entryId);String filename=content.filename();filename=filename.substring(filename.lastIndexOf('/')+1);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header("Content-Disposition",ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(content.contents()));
    }
    @ExceptionHandler(DatasetVersionConflictException.class) public ResponseEntity<Void> conflict() { return ResponseEntity.status(HttpStatus.CONFLICT).build(); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Void> invalid() { return ResponseEntity.badRequest().build(); }
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<Void> missing() { return ResponseEntity.notFound().build(); }
}
