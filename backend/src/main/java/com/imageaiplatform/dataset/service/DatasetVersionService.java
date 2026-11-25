package com.imageaiplatform.dataset.service;

import com.imageaiplatform.dataset.dao.DatasetDao;
import com.imageaiplatform.dataset.dao.DatasetVersionDao;
import com.imageaiplatform.dataset.dao.DatasetImageDao;
import com.imageaiplatform.dataset.dao.DatasetFileDao;
import com.imageaiplatform.dataset.domain.DatasetImageHistory;
import com.imageaiplatform.dataset.domain.DatasetFileHistory;
import com.imageaiplatform.dataset.domain.DatasetVersionConflictException;
import com.imageaiplatform.dataset.domain.DatasetVersion;
import com.imageaiplatform.dataset.domain.DatasetVersionDraft;
import com.imageaiplatform.dataset.domain.DatasetVersionEntry;
import com.imageaiplatform.dataset.domain.DatasetListItem;
import com.imageaiplatform.dataset.domain.ImageStorageProperties;
import com.imageaiplatform.dataset.domain.FileStorageProperties;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetVersionService {
    private final DatasetVersionDao versions;
    private final DatasetDao datasets;
    private final DatasetImageDao images;
    private final DatasetFileDao files;
    private final Path imageDirectory;
    private final Path fileDirectory;
    public DatasetVersionService(DatasetVersionDao versions, DatasetDao datasets, ImageStorageProperties imageStorage, FileStorageProperties fileStorage, DatasetImageDao images, DatasetFileDao files) {
        this.versions=versions; this.datasets=datasets; this.images=images; this.files=files;
        imageDirectory=imageStorage.directory().toAbsolutePath().normalize(); fileDirectory=fileStorage.directory().toAbsolutePath().normalize();
    }
    private String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private DatasetListItem accessible(String datasetId) {
        var dataset=datasets.findVisibleById(datasetId,actor());
        if(dataset==null) throw new AccessDeniedException("Active workspace membership is required.");
        return dataset;
    }
    private DatasetVersion find(String datasetId,String versionId) {
        accessible(datasetId);
        var version=versions.find(datasetId,versionId);
        if(version==null) throw new NoSuchElementException("Dataset version is unavailable.");
        return version;
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:read')")
    @Transactional(value="appTransactionManager",readOnly=true)
    public List<DatasetListItem> targets() { return datasets.findVisibleToUser(actor()); }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:create') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetVersion create(String datasetId,String changeNote) {
        lockDataset(datasetId);
        var dataset=accessible(datasetId);
        var version = snapshot(datasetId, datasetId, dataset.name(), dataset.description(), changeNote);
        versions.deleteDraft(datasetId);
        return version;
    }
    private DatasetVersion snapshot(String datasetId, String workingDatasetId, String name, String description, String changeNote) {
        var now=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);var actor=actor();
        var version=new DatasetVersion(UUID.randomUUID().toString(),datasetId,versions.nextNumber(datasetId),name,description,
                normalizeNote(changeNote),Long.valueOf(0),Long.valueOf(0),now,now,actor,actor,null,null);
        versions.insert(version);
        versions.snapshotImages(workingDatasetId,version.versionId(),now,actor);
        versions.snapshotFiles(workingDatasetId,version.versionId(),now,actor);
        return versions.find(datasetId,version.versionId());
    }
    private String normalizeNote(String note) {
        if (note != null && note.length() > 2000) throw new IllegalArgumentException("Change note must not exceed 2000 characters.");
        return note == null || note.isBlank() ? null : note;
    }
    private void lockDataset(String datasetId) {
        if (datasets.lockForDeletion(datasetId, actor()) == null) throw new AccessDeniedException("Dataset management is not allowed.");
    }
    private DatasetVersionDraft requireDraft(String datasetId, String draftId) {
        var draft = versions.findDraft(datasetId, draftId);
        if (draft == null) throw new NoSuchElementException("Dataset version draft is unavailable.");
        return draft;
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:read')")
    @Transactional(value="appTransactionManager", readOnly=true)
    public List<DatasetVersionDraft> drafts(String datasetId) {
        accessible(datasetId);
        return versions.listDrafts(datasetId);
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:restore') and @authorizationService.hasPermission('dataset:version:read') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetVersionDraft startDraft(String datasetId, String versionId, String name) {
        lockDataset(datasetId);
        var base = find(datasetId, versionId);
        var entries = versions.entries(versionId);
        validateSourceFiles(versionId, entries);
        var id = UUID.randomUUID().toString();
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var draft = new DatasetVersionDraft(id, datasetId, id, versionId, draftName(name, "v" + base.versionNumber() + " 편집본 · " + id.substring(0, 8)),
                null, now, now, actor(), actor(), null, null, null, null, base.versionNumber());
        versions.insertWorkingDataset(draft, base);
        versions.insertDraft(draft);
        for (var entry : entries) {
            String entryId = UUID.randomUUID().toString();
            if (entry.kind().equals("images")) versions.copyDraftImage(draft, entry, entryId);
            else versions.copyDraftFile(draft, entry, entryId);
        }
        return requireDraft(datasetId, id);
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:create') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetVersionDraft saveNamedDraft(String datasetId, String draftId, String name, String changeNote) {
        lockDataset(datasetId);
        return updateDraft(requireDraft(datasetId, draftId), name, changeNote);
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:create') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetVersion publishDraft(String datasetId, String draftId, String changeNote) {
        lockDataset(datasetId);
        var draft = requireDraft(datasetId, draftId);
        var base = versions.findDraftBase(datasetId, draft.baseVersionId());
        boolean legacy = datasetId.equals(draft.workingDatasetId());
        if (!legacy && versions.lockWorkingDataset(draft.workingDatasetId()) == null) {
            throw new NoSuchElementException("Working dataset is unavailable.");
        }
        var dataset = accessible(datasetId);
        var version = snapshot(datasetId, draft.workingDatasetId(), legacy ? dataset.name() : base.name(),
                legacy ? dataset.description() : base.description(), changeNote);
        versions.deleteDraft(draft.draftId());
        return version;
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:item:delete') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public void deleteVersion(String datasetId, String versionId) {
        lockDataset(datasetId);
        find(datasetId, versionId);
        versions.markVersionDeleted(datasetId, versionId, OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS), actor());
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:item:delete') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public void deleteNamedDraft(String datasetId, String draftId) {
        lockDataset(datasetId);
        var draft = requireDraft(datasetId, draftId);
        if (!datasetId.equals(draft.workingDatasetId())) versions.lockWorkingDataset(draft.workingDatasetId());
        versions.deleteDraft(draftId);
    }
    private String draftName(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback;
        if (name.length() > 200) throw new IllegalArgumentException("Draft name must not exceed 200 characters.");
        return name.strip();
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:read')")
    @Transactional(value="appTransactionManager",readOnly=true)
    public DatasetVersionDraft draft(String datasetId) {
        accessible(datasetId);
        return versions.findDraft(datasetId);
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:create') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetVersionDraft saveDraft(String datasetId, String changeNote) {
        lockDataset(datasetId);
        return updateDraft(requireDraft(datasetId, datasetId), null, changeNote);
    }
    private DatasetVersionDraft updateDraft(DatasetVersionDraft draft, String name, String changeNote) {
        var saved = new DatasetVersionDraft(draft.draftId(), draft.datasetId(), draft.workingDatasetId(), draft.baseVersionId(),
                draftName(name, draft.name()), normalizeNote(changeNote),
                draft.createdAt(), OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS), draft.createdBy(), actor(),
                null, null, null, null, draft.baseVersionNumber());
        versions.updateDraft(saved);
        return requireDraft(draft.datasetId(), draft.draftId());
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:read')")
    @Transactional(value="appTransactionManager",readOnly=true)
    public List<DatasetVersion> list(String datasetId) { accessible(datasetId);return versions.list(datasetId); }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:read')")
    @Transactional(value="appTransactionManager",readOnly=true)
    public DatasetVersion detail(String datasetId,String versionId) { return find(datasetId,versionId); }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:read')")
    @Transactional(value="appTransactionManager",readOnly=true)
    public List<DatasetVersionEntry> entries(String datasetId,String versionId) { find(datasetId,versionId);return versions.entries(versionId); }
    public record Content(String filename, InputStream contents) {}
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:read')")
    @Transactional(value="appTransactionManager", readOnly=true)
    public InputStream thumbnail(String datasetId, String versionId, String imageId) {
        find(datasetId, versionId);
        var location = versions.imageLocation(versionId, imageId);
        if (location == null || location.thumbnailPath() == null) throw new NoSuchElementException("Thumbnail is unavailable.");
        try { return Files.newInputStream(resolve("images", location.thumbnailPath())); }
        catch (NoSuchFileException exception) { throw new NoSuchElementException("Thumbnail is unavailable."); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:read')")
    @Transactional(value="appTransactionManager",readOnly=true)
    public Content content(String datasetId,String versionId,String kind,String entryId) {
        find(datasetId,versionId);
        var location=switch(kind) {
            case "images" -> versions.imageLocation(versionId,entryId);
            case "files" -> versions.fileLocation(versionId,entryId);
            default -> throw new IllegalArgumentException("Unknown file kind.");
        };
        if(location==null) throw new NoSuchElementException("File is not part of the version.");
        try { return new Content(location.originalFilename(),Files.newInputStream(resolve(kind,location.storagePath()))); }
        catch(NoSuchFileException exception) { throw new NoSuchElementException("File is unavailable."); }
        catch(IOException exception) { throw new UncheckedIOException(exception); }
    }
    private Path resolve(String kind,String storagePath) throws IOException {
        Path root=(kind.equals("images")?imageDirectory:fileDirectory).toRealPath();
        Path file=root.resolve(storagePath).normalize().toRealPath();
        if(!file.startsWith(root) || !Files.isRegularFile(file)) throw new NoSuchFileException("File is unavailable.");
        return file;
    }
    @PreAuthorize("@authorizationService.hasPermission('dataset:version:restore') and @authorizationService.hasPermission('dataset:version:read') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetVersion restore(String datasetId,String versionId) {
        if(datasets.lockForDeletion(datasetId,actor())==null) throw new AccessDeniedException("Dataset management is not allowed.");
        var version=find(datasetId,versionId);var entries=versions.entries(versionId);
        validateSourceFiles(versionId, entries);
        var now=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);String actor=actor();
        var draft = new DatasetVersionDraft(datasetId, datasetId, datasetId, versionId, "v" + version.versionNumber() + " 편집본",
                null, now, now, actor, actor, null, null, null, null, version.versionNumber());
        for(var image:images.findByDatasetId(datasetId)) {
            var removed=image.withDeletion(now,actor);
            requireOne(images.markDeleted(removed));
            requireOne(images.insertHistory(new DatasetImageHistory(UUID.randomUUID().toString(),"DELETE",removed,now,actor)));
        }
        for(var file:files.findByDatasetId(datasetId)) {
            var removed=file.withDeletion(now,actor);
            requireOne(files.markDeleted(removed));
            requireOne(files.insertHistory(new DatasetFileHistory(UUID.randomUUID().toString(),"DELETE",removed,now,actor)));
        }
        for(var entry:entries) {
            if(entry.kind().equals("images")) {
                if (versions.restoreImage(datasetId, entry, now, actor) == 0) versions.copyDraftImage(draft, entry, UUID.randomUUID().toString());
                requireOne(images.insertHistory(new DatasetImageHistory(UUID.randomUUID().toString(),"UPDATE",images.findActiveByFilename(datasetId,entry.originalFilename()),now,actor)));
            } else {
                if (versions.restoreFile(datasetId, entry, now, actor) == 0) versions.copyDraftFile(draft, entry, UUID.randomUUID().toString());
                requireOne(files.insertHistory(new DatasetFileHistory(UUID.randomUUID().toString(),"UPDATE",files.findActiveByFilename(datasetId,entry.originalFilename()),now,actor)));
            }
        }
        requireOne(versions.restoreMetadata(version,now,actor));
        versions.deleteDraft(datasetId);
        versions.insertDraft(draft);
        return version;
    }
    private void validateSourceFiles(String versionId, List<DatasetVersionEntry> entries) {
        for (var entry : entries) {
            var location = entry.kind().equals("images") ? versions.imageLocation(versionId, entry.entryId()) : versions.fileLocation(versionId, entry.entryId());
            try {
                if (location == null) throw new NoSuchFileException("File is unavailable.");
                Path file = resolve(entry.kind(), location.storagePath());
                if (!Files.isReadable(file) || Files.size(file) != entry.sizeBytes()) throw new DatasetVersionConflictException("A source file is unavailable or has changed size.");
            } catch (IOException exception) {
                throw new DatasetVersionConflictException("A source file is unavailable.");
            }
        }
    }
    private void requireOne(Integer count) {
        if(!Integer.valueOf(1).equals(count)) throw new AccessDeniedException("Dataset access changed before restoration completed.");
    }
}
