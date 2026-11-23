package com.imageaiplatform.dataset.service;

import com.imageaiplatform.dataset.dao.DatasetDao;
import com.imageaiplatform.dataset.dao.DatasetVersionDao;
import com.imageaiplatform.dataset.dao.DatasetImageDao;
import com.imageaiplatform.dataset.dao.DatasetFileDao;
import com.imageaiplatform.dataset.domain.DatasetImageHistory;
import com.imageaiplatform.dataset.domain.DatasetFileHistory;
import com.imageaiplatform.dataset.domain.DatasetVersionConflictException;
import com.imageaiplatform.dataset.domain.DatasetVersion;
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
        if(changeNote!=null && changeNote.length()>2000) throw new IllegalArgumentException("Change note must not exceed 2000 characters.");
        if(datasets.lockForDeletion(datasetId,actor())==null) throw new AccessDeniedException("Dataset management is not allowed.");
        var dataset=accessible(datasetId); var now=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);var actor=actor();
        var version=new DatasetVersion(UUID.randomUUID().toString(),datasetId,versions.nextNumber(datasetId),dataset.name(),dataset.description(),
                changeNote==null || changeNote.isBlank()?null:changeNote,Long.valueOf(0),Long.valueOf(0),now,now,actor,actor,null,null);
        versions.insert(version);
        versions.snapshotImages(datasetId,version.versionId(),now,actor);
        versions.snapshotFiles(datasetId,version.versionId(),now,actor);
        return versions.find(datasetId,version.versionId());
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
        // Validate every source before changing the working copy. No file bytes are written.
        for(var entry:entries) {
            var location=entry.kind().equals("images")?versions.imageLocation(versionId,entry.entryId()):versions.fileLocation(versionId,entry.entryId());
            try {
                if(location==null) throw new NoSuchFileException("File is unavailable.");
                Path file=resolve(entry.kind(),location.storagePath());
                if(!Files.isReadable(file) || Files.size(file)!=entry.sizeBytes()) throw new DatasetVersionConflictException("A source file is unavailable or has changed size.");
            } catch(IOException exception) { throw new DatasetVersionConflictException("A source file is unavailable."); }
        }
        var now=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);String actor=actor();
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
                requireOne(versions.restoreImage(datasetId,entry,now,actor));
                requireOne(images.insertHistory(new DatasetImageHistory(UUID.randomUUID().toString(),"UPDATE",images.findById(datasetId,entry.entryId()),now,actor)));
            } else {
                requireOne(versions.restoreFile(datasetId,entry,now,actor));
                requireOne(files.insertHistory(new DatasetFileHistory(UUID.randomUUID().toString(),"UPDATE",files.findById(datasetId,entry.entryId()),now,actor)));
            }
        }
        requireOne(versions.restoreMetadata(version,now,actor));
        return version;
    }
    private void requireOne(Integer count) {
        if(!Integer.valueOf(1).equals(count)) throw new AccessDeniedException("Dataset access changed before restoration completed.");
    }
}
