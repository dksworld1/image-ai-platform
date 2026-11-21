package com.imageaiplatform.artifact.controller;

import com.imageaiplatform.artifact.service.ArtifactService;
import com.imageaiplatform.artifact.domain.Artifact;
import com.imageaiplatform.artifact.domain.ArtifactFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {
    private final ArtifactService service;
    public ArtifactController(ArtifactService service) { this.service = service; }
    public record CreateRequest(String workspaceId, String dataType, String schemaName, String schemaVersion,
            String description, OffsetDateTime retentionUntil, Boolean protectedFlag) {}
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Artifact create(@RequestBody CreateRequest request) {
        return service.createArtifact(request.workspaceId(), request.dataType(), request.schemaName(), request.schemaVersion(),
                request.description(), request.retentionUntil(), request.protectedFlag());
    }
    @GetMapping public List<Artifact> list(@RequestParam String workspaceId) { return service.artifacts(workspaceId); }
    @GetMapping("/{id}") public Artifact detail(@PathVariable String id) { return service.artifact(id); }
    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactFile upload(@PathVariable String id, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String internalName) throws IOException {
        try (var contents = file.getInputStream()) {
            return service.addFile(id, internalName == null ? file.getOriginalFilename() : internalName, file.getContentType(), contents, file.getSize());
        }
    }
    @GetMapping("/{id}/files") public List<ArtifactFile> files(@PathVariable String id) { return service.files(id); }
    @PostMapping("/{id}/complete") public Artifact complete(@PathVariable String id) { return service.completeArtifact(id); }
    @PostMapping("/{id}/fail") public Artifact fail(@PathVariable String id) { return service.failArtifact(id); }
    @GetMapping("/{id}/files/{fileId}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable String id, @PathVariable String fileId) {
        var result = service.content(id, fileId); String name = result.file().internalName();
        name = name.substring(name.lastIndexOf('/') + 1);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .contentLength(result.file().sizeBytes())
                .header("Content-Disposition", ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(result.contents()));
    }
}
