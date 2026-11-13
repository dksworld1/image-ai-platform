package com.imageaiplatform.dataset.service;

import com.imageaiplatform.dataset.dao.DatasetFileDao;
import com.imageaiplatform.dataset.domain.DatasetFile;
import com.imageaiplatform.dataset.domain.DatasetFileHistory;
import com.imageaiplatform.dataset.domain.DatasetFileContent;
import com.imageaiplatform.dataset.domain.DatasetFileTarget;
import com.imageaiplatform.auth.service.AuthorizationService;
import com.imageaiplatform.dataset.domain.FileStorageProperties;
import com.imageaiplatform.dataset.domain.FileDescriptionChange;
import com.imageaiplatform.dataset.domain.UploadFilename;
import java.util.ArrayList;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.NoSuchFileException;


import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;


import java.util.UUID;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@EnableConfigurationProperties(FileStorageProperties.class)
public class DatasetFileService {
    private static final Logger log = LoggerFactory.getLogger(DatasetFileService.class);
    private final DatasetFileDao fileDao;
    private final AuthorizationService authorizationService;
    private final Path directory;
    private final long maxFileSizeBytes;

    public DatasetFileService(DatasetFileDao fileDao, AuthorizationService authorizationService, FileStorageProperties properties) {
        this.fileDao = fileDao;
        this.authorizationService = authorizationService;
        this.directory = properties.directory().toAbsolutePath().normalize();
        this.maxFileSizeBytes = properties.maxFileSize().toBytes();
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:file:upload') and @datasetFileService.canUpload(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetFile upload(String datasetId, String originalFilename, byte[] contents, String description, String relativePath) {
        return store(datasetId, originalFilename, contents, description, relativePath, false);
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:file:update') and @datasetFileService.canUpdate(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetFile replace(String datasetId, String originalFilename, byte[] contents, String description, String relativePath) {
        return store(datasetId, originalFilename, contents, description, relativePath, true);
    }

    private DatasetFile store(String datasetId, String originalFilename, byte[] contents, String description, String relativePath, boolean replaceExisting) {
        description = normalizeDescription(description);
        String filename = UploadFilename.resolve(originalFilename, relativePath);
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        if (fileDao.lockDataset(datasetId, actor) == null) {
            throw new AccessDeniedException("Dataset is unavailable or upload is not allowed.");
        }
        var previous = fileDao.findActiveByFilename(datasetId, filename);
        if (replaceExisting && previous == null) {
            authorizationService.hasPermission("dataset:file:upload");
        }
        if (previous != null && !replaceExisting) {
            throw new DuplicateKeyException("A file with this original filename already exists in the dataset.");
        }
        if (contents == null || contents.length == 0 || contents.length > maxFileSizeBytes) {
            throw new IllegalArgumentException("File is empty or exceeds the configured maximum size.");
        }

        String fileId = UUID.randomUUID().toString();
        String basename = filename.substring(filename.lastIndexOf('/') + 1);
        int extensionIndex = basename.lastIndexOf('.');
        String extension = extensionIndex > 0 && extensionIndex < basename.length() - 1 ? basename.substring(extensionIndex) : "";
        String storagePath = fileId + extension;
        writeFile(directory.resolve(storagePath), contents);
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (previous != null) {
            var removed = previous.withDeletion(now, actor);
            if (!Integer.valueOf(1).equals(fileDao.markDeleted(removed))) {
                throw new AccessDeniedException("Dataset access changed before the file could be replaced.");
            }
            recordHistory("DELETE", removed, now, actor);
        }
        var entry = new DatasetFile(fileId, datasetId, filename, storagePath, "application/octet-stream",
                Long.valueOf(contents.length), now, now, actor, actor, Boolean.FALSE,
                previous == null ? Integer.valueOf(1) : Integer.valueOf(Math.addExact(previous.versionCount(), 1)),
                previous == null ? null : previous.fileId(), null, null, null, null,
                previous == null ? now : previous.firstRegisteredAt(), description);
        if (!Integer.valueOf(1).equals(fileDao.insert(entry))) {
            throw new AccessDeniedException("Dataset access changed before the file could be stored.");
        }
        recordHistory(previous == null ? "CREATE" : "UPDATE", entry, now, actor);
        return fileDao.findById(datasetId, fileId);
    }

    private void recordHistory(String action, DatasetFile entry, OffsetDateTime now, String actor) {
        if (!Integer.valueOf(1).equals(fileDao.insertHistory(new DatasetFileHistory(UUID.randomUUID().toString(), action, entry, now, actor)))) {
            throw new IllegalStateException("Could not record file history.");
        }
    }

    private String normalizeDescription(String description) {
        if (description != null && description.length() > 2000) throw new IllegalArgumentException("Description must not exceed 2000 characters.");
        return description == null || description.isBlank() ? null : description;
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:file:update') and @datasetFileService.canUpdate(#datasetId)")
    @Transactional("appTransactionManager")
    public List<DatasetFile> updateDescriptions(String datasetId, List<FileDescriptionChange> changes) {
        if (changes == null || changes.isEmpty() || changes.stream().map(FileDescriptionChange::fileId).distinct().count() != changes.size()) {
            throw new IllegalArgumentException("Distinct entry IDs are required.");
        }
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        if (fileDao.lockDataset(datasetId, actor) == null) throw new AccessDeniedException("File update is not allowed.");
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var result = new ArrayList<DatasetFile>();
        for (var change : changes) {
            var entry = fileDao.findById(datasetId, change.fileId());
            if (entry == null) throw new NoSuchElementException("File is unavailable.");
            var updated = entry.withDescription(normalizeDescription(change.description()), now, actor);
            if (!Integer.valueOf(1).equals(fileDao.updateDescription(updated))) throw new AccessDeniedException("File update is no longer allowed.");
            recordHistory("UPDATE", updated, now, actor);
            result.add(fileDao.findById(datasetId, entry.fileId()));
        }
        return List.copyOf(result);
    }

    public boolean canRead(String datasetId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && Boolean.TRUE.equals(fileDao.canRead(datasetId, authentication.getName()));
    }

    public boolean canUpload(String datasetId) {
        return canManage(datasetId);
    }

    public boolean canUpdate(String datasetId) {
        return canManage(datasetId);
    }

    public boolean canDelete(String datasetId) {
        return canManage(datasetId);
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:file:delete') and @datasetFileService.canDelete(#datasetId)")
    @Transactional("appTransactionManager")
    public void delete(String datasetId, String fileId) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        if (fileDao.lockDataset(datasetId, actor) == null) {
            throw new AccessDeniedException("Dataset is unavailable or entry deletion is not allowed.");
        }
        var entry = fileDao.findById(datasetId, fileId);
        if (entry == null) throw new NoSuchElementException("File is unavailable.");
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var removed = entry.withDeletion(now, actor);
        if (!Integer.valueOf(1).equals(fileDao.markDeleted(removed))) {
            throw new AccessDeniedException("Dataset access changed before the file could be deleted.");
        }
        recordHistory("DELETE", removed, now, actor);
    }

    private boolean canManage(String datasetId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && Boolean.TRUE.equals(fileDao.canManage(datasetId, authentication.getName()));
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:file:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<DatasetFileTarget> targets() {
        return fileDao.findTargets(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:file:read') and @datasetFileService.canRead(#datasetId)")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<DatasetFile> list(String datasetId) {
        return fileDao.findByDatasetId(datasetId);
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:file:read') and @datasetFileService.canRead(#datasetId)")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public DatasetFileContent content(String datasetId, String fileId) {
        var entry = fileDao.findById(datasetId, fileId);
        if (entry == null) throw new NoSuchElementException("File is unavailable.");
        return new DatasetFileContent(readFile(entry.storagePath()), entry.mediaType(), entry.originalFilename());
    }

    private byte[] readFile(String storagePath) {
        Path file = directory.resolve(storagePath).normalize();
        if (!file.startsWith(directory)) throw new NoSuchElementException("Image file is unavailable.");
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException exception) {
            throw new NoSuchElementException("Image file is unavailable.");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the data file.", exception);
        }
    }

    private void writeFile(Path file, byte[] contents) {
        try {
            Files.createDirectories(directory);
            try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try {
                                Files.deleteIfExists(file);
                            } catch (IOException exception) {
                                log.warn("Could not remove rolled-back entry {}", file, exception);
                            }
                        }
                    }
                });
                output.write(contents);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store the file.", exception);
        }
    }

}
