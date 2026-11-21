package com.imageaiplatform.artifact.service;

import com.imageaiplatform.artifact.dao.ArtifactDao;
import com.imageaiplatform.artifact.domain.Artifact;
import com.imageaiplatform.artifact.domain.ArtifactFile;
import com.imageaiplatform.artifact.domain.ArtifactSet;
import com.imageaiplatform.artifact.domain.ArtifactSetVersion;
import com.imageaiplatform.artifact.domain.ArtifactSetMember;
import com.imageaiplatform.artifact.domain.ArtifactFileStore;
import com.imageaiplatform.artifact.domain.ArtifactConflictException;
import com.imageaiplatform.workspace.service.WorkspaceService;
import java.io.InputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
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
    private final ArtifactFileStore store;
    public ArtifactService(ArtifactDao dao, WorkspaceService workspaces, ArtifactFileStore store) {
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
    private ArtifactSet accessibleSet(String id, boolean lock) {
        var value = lock ? dao.lockSet(id) : dao.set(id);
        if (value == null) throw new AccessDeniedException("Artifact set is unavailable.");
        member(value.workspaceId()); return value;
    }
    private ArtifactSetVersion accessibleVersion(String setId, String versionId, boolean lock) {
        accessibleSet(setId, false);
        var value = lock ? dao.lockVersion(versionId) : dao.version(versionId);
        if (value == null || !value.setId().equals(setId)) throw new AccessDeniedException("Set version is unavailable.");
        return value;
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
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { store.remove(root, relativePath); }
                    catch (RuntimeException failure) { log.warn("Could not remove uncommitted artifact file {}/{}", root, relativePath, failure); }
                }
            }
        });
        var facts = store.write(root, relativePath, contents, size);
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
        return new FileContent(file, store.open(file));
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:update')")
    @Transactional("appTransactionManager")
    public Artifact completeArtifact(String id) {
        var value = accessibleArtifact(id, true);
        if ("AVAILABLE".equals(value.status())) return value;
        writing(value);
        var files = dao.files(id);
        if (files.isEmpty()) throw new ArtifactConflictException("An artifact requires at least one file.");
        for (var file : files) store.verify(file);
        dao.artifactStatus(id, "AVAILABLE", actor()); return dao.artifact(id);
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifact:update')")
    @Transactional("appTransactionManager")
    public Artifact failArtifact(String id) {
        var value = accessibleArtifact(id, true);
        if ("FAILED".equals(value.status())) return value;
        writing(value); dao.artifactStatus(id, "FAILED", actor()); return dao.artifact(id);
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:create')")
    @Transactional("appTransactionManager")
    public ArtifactSet createSet(String workspaceId, String name, String purpose, String description) {
        workspaceId = text(workspaceId, 36, true); member(workspaceId);
        var time = now(); var actor = actor();
        var value = new ArtifactSet(UUID.randomUUID().toString(), workspaceId, text(name, 200, true), text(purpose, 100, true), description(description), time, time, actor, actor);
        dao.insertSet(value); return value;
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public ArtifactSet set(String id) { return accessibleSet(id, false); }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<ArtifactSet> sets(String workspaceId) { workspaceId = text(workspaceId, 36, true); member(workspaceId); return dao.sets(workspaceId); }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:update')")
    @Transactional("appTransactionManager")
    public ArtifactSetVersion createVersion(String setId) {
        accessibleSet(setId, true);
        var time = now(); var actor = actor();
        var value = new ArtifactSetVersion(UUID.randomUUID().toString(), setId, dao.nextVersionNumber(setId), "DRAFT", null, null, null, time, time, actor, actor);
        dao.insertVersion(value); return value;
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<ArtifactSetVersion> versions(String setId) { accessibleSet(setId, false); return dao.versions(setId); }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public ArtifactSetVersion version(String setId, String versionId) { return accessibleVersion(setId, versionId, false); }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<ArtifactSetMember> members(String setId, String versionId) { accessibleVersion(setId, versionId, false); return dao.members(versionId); }
    public record MemberInput(String artifactId, Long ordinal, String sourceKey) {}
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:update') and @authorizationService.hasPermission('pipeline:artifact:read')")
    @Transactional("appTransactionManager")
    public List<ArtifactSetMember> replaceMembers(String setId, String versionId, List<MemberInput> members) {
        var version = accessibleVersion(setId, versionId, true);
        if (!"DRAFT".equals(version.status())) throw new ArtifactConflictException("A confirmed version cannot be changed.");
        if (members == null) throw new IllegalArgumentException("Members are required; use an empty list for an empty set.");
        var set = dao.set(setId); var ids = new HashSet<String>(); var positions = new HashSet<Long>();
        var time = now(); var actor = actor();
        dao.deleteMembers(versionId);
        for (var input : members) {
            if (input == null || input.ordinal() == null || input.ordinal() < 0 || !positions.add(input.ordinal()) || !ids.add(input.artifactId())) throw new IllegalArgumentException("Duplicate artifact or ordinal, or invalid member.");
            var artifact = accessibleArtifact(input.artifactId(), false);
            if (!artifact.workspaceId().equals(set.workspaceId())) throw new AccessDeniedException("Cross-workspace membership is not allowed.");
            if (!"AVAILABLE".equals(artifact.status())) throw new ArtifactConflictException("Set members must be available artifacts.");
            dao.insertMember(new ArtifactSetMember(versionId, artifact.artifactId(), input.ordinal(), text(input.sourceKey(), 1024, false), time, time, actor, actor));
        }
        return dao.members(versionId);
    }
    @PreAuthorize("@authorizationService.hasPermission('pipeline:artifactset:update')")
    @Transactional("appTransactionManager")
    public ArtifactSetVersion confirmVersion(String setId, String versionId) {
        var value = accessibleVersion(setId, versionId, true);
        if ("CONFIRMED".equals(value.status())) return value;
        var members = dao.members(versionId);
        for (var item : members) {
            if (!"AVAILABLE".equals(accessibleArtifact(item.artifactId(), false).status())) throw new ArtifactConflictException("A member is not available.");
        }
        dao.confirmVersion(versionId, Long.valueOf(members.size()), composition(members), actor());
        return dao.version(versionId);
    }
    private String composition(List<ArtifactSetMember> members) {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), hash))) {
                output.writeUTF("artifact-set-v1"); output.writeInt(members.size());
                for (var item : members.stream().sorted(Comparator.comparing(ArtifactSetMember::ordinal)).toList()) {
                    output.writeLong(item.ordinal()); output.writeUTF(item.artifactId());
                    output.writeBoolean(item.sourceKey() != null);
                    if (item.sourceKey() != null) output.writeUTF(item.sourceKey());
                }
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (IOException | NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
