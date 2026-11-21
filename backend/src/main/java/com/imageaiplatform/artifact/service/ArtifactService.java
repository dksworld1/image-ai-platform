package com.imageaiplatform.artifact.service;

import com.imageaiplatform.artifact.dao.ArtifactDao;
import com.imageaiplatform.artifact.domain.Artifact;
import com.imageaiplatform.artifact.domain.ArtifactFile;
import com.imageaiplatform.artifact.domain.ArtifactConflictException;
import com.imageaiplatform.storage.domain.ArtifactStorage;
import com.imageaiplatform.workspace.service.WorkspaceService;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ArtifactService {
    private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);
    private final ArtifactDao dao;
    private final WorkspaceService workspaces;
    private final ArtifactStorage store;
    public ArtifactService(ArtifactDao dao, WorkspaceService workspaces, ArtifactStorage store) {
        this.dao = dao; this.workspaces = workspaces; this.store = store;
    }
    private String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }
    private String text(String value, int limit, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new IllegalArgumentException("A required value is missing.");
            return null;
        }
        if (value.length() > limit || value.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("Invalid text value.");
        return value.trim();
    }
    private String description(String value) {
        if (value != null && value.length() > 2000) throw new IllegalArgumentException("Description is too long.");
        return value == null || value.isBlank() ? null : value;
    }
    private void member(String workspaceId) {
        if (!workspaces.isMemberOfAll(List.of(workspaceId))) throw new AccessDeniedException("Active workspace membership is required.");
    }
    private Artifact accessibleArtifact(String id, boolean lock) {
        var value = lock ? dao.lockArtifact(id) : dao.artifact(id);
        if (value == null) throw new AccessDeniedException("Artifact is unavailable.");
        member(value.workspaceId()); return value;
    }
    private void verifyFile(ArtifactFile file) {
        try {
            var facts = store.inspect(file.rootKey(), file.relativePath());
            if (!facts.sizeBytes().equals(file.sizeBytes()) || !facts.checksum().equals(file.checksum())) {
                throw new ArtifactConflictException("Artifact file size or checksum does not match.");
            }
        } catch (UncheckedIOException failure) {
            throw new ArtifactConflictException("Artifact file is unavailable.");
        }
    }

    private void writing(Artifact value) {
        if (!"WRITING".equals(value.status())) throw new ArtifactConflictException("Only a WRITING artifact can be changed.");
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:create')")
    @Transactional("appTransactionManager")
    public Artifact createArtifact(String workspaceId, String dataType, String schemaName, String schemaVersion,
            String description, OffsetDateTime retentionUntil, Boolean protectedFlag) {
        workspaceId = text(workspaceId, 36, true); member(workspaceId);
        schemaName = text(schemaName, 200, false); schemaVersion = text(schemaVersion, 100, false);
        if ((schemaName == null) != (schemaVersion == null)) throw new IllegalArgumentException("Schema name and version must be supplied together.");
        var time = now(); var actor = actor();
        var value = new Artifact(UUID.randomUUID().toString(), workspaceId, text(dataType, 100, true), schemaName, schemaVersion,
                "WRITING", description(description), retentionUntil, Boolean.TRUE.equals(protectedFlag), time, time, actor, actor);
        dao.insertArtifact(value); return value;
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public Artifact artifact(String id) { return accessibleArtifact(id, false); }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<Artifact> artifacts(String workspaceId) { workspaceId = text(workspaceId, 36, true); member(workspaceId); return dao.artifacts(workspaceId); }

    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:update')")
    @Transactional("appTransactionManager")
    public ArtifactFile addFile(String id, String internalName, String mediaType, InputStream contents, long size) {
        writing(accessibleArtifact(id, true));
        internalName = text(internalName, 1024, true).replace('\\', '/');
        for (String part : internalName.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..") || part.indexOf(':') >= 0 || part.length() > 255) throw new IllegalArgumentException("Invalid internal filename.");
        }
        mediaType = text(mediaType, 200, false);
        if (mediaType == null) mediaType = "application/octet-stream";
        org.springframework.http.MediaType.parseMediaType(mediaType);
        String fileId = UUID.randomUUID().toString(); String root = store.defaultRoot(); String relativePath = id + "/" + fileId;
        var facts = store.write(root, relativePath, contents, size);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { store.remove(root, relativePath); }
                    catch (RuntimeException failure) { log.warn("Could not remove uncommitted artifact file {}/{}", root, relativePath, failure); }
                }
            }
        });
        var time = now(); var actor = actor();
        var value = new ArtifactFile(fileId, id, internalName, root, relativePath, mediaType, facts.sizeBytes(), "SHA-256", facts.checksum(), null, time, time, actor, actor);
        dao.insertFile(value); dao.artifactStatus(id, "WRITING", actor);
        return value;
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<ArtifactFile> files(String id) { accessibleArtifact(id, false); return dao.files(id); }
    public record FileContent(ArtifactFile file, InputStream contents) {}
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public FileContent content(String id, String fileId) {
        if (!"AVAILABLE".equals(accessibleArtifact(id, false).status())) throw new ArtifactConflictException("Artifact is not available.");
        var file = dao.file(id, fileId);
        if (file == null || file.deletedAt() != null) throw new NoSuchElementException("Artifact file is unavailable.");
        return new FileContent(file, store.open(file.rootKey(), file.relativePath()));
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:update')")
    @Transactional("appTransactionManager")
    public Artifact completeArtifact(String id) {
        var value = accessibleArtifact(id, true);
        if ("AVAILABLE".equals(value.status())) return value;
        writing(value);
        var files = dao.files(id);
        if (files.isEmpty()) throw new ArtifactConflictException("An artifact requires at least one file.");
        for (var file : files) verifyFile(file);
        dao.artifactStatus(id, "AVAILABLE", actor()); return dao.artifact(id);
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:update')")
    @Transactional("appTransactionManager")
    public Artifact failArtifact(String id) {
        var value = accessibleArtifact(id, true);
        if ("FAILED".equals(value.status())) return value;
        writing(value); dao.artifactStatus(id, "FAILED", actor()); return dao.artifact(id);
    }
}
