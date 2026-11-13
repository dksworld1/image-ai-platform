package com.imageaiplatform.dataset.service;

import com.imageaiplatform.dataset.dao.DatasetImageDao;
import com.imageaiplatform.dataset.domain.DatasetImage;
import com.imageaiplatform.dataset.domain.DatasetImageHistory;
import com.imageaiplatform.dataset.domain.DatasetImageContent;
import com.imageaiplatform.dataset.domain.DatasetImageTarget;
import com.imageaiplatform.auth.service.AuthorizationService;
import com.imageaiplatform.dataset.domain.ImageStorageProperties;
import com.imageaiplatform.dataset.domain.ImageDescriptionChange;
import com.imageaiplatform.dataset.domain.UploadFilename;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.NoSuchFileException;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.NoSuchElementException;
import javax.imageio.ImageIO;
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
@EnableConfigurationProperties(ImageStorageProperties.class)
public class DatasetImageService {
    private static final Logger log = LoggerFactory.getLogger(DatasetImageService.class);
    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "png", "image/png", "jpeg", "image/jpeg", "jpg", "image/jpeg", "gif", "image/gif", "bmp", "image/bmp");
    private final DatasetImageDao imageDao;
    private final AuthorizationService authorizationService;
    private final Path directory;
    private final long maxFileSizeBytes;
    private final Integer thumbnailMaxEdge;

    public DatasetImageService(DatasetImageDao imageDao, AuthorizationService authorizationService, ImageStorageProperties properties) {
        this.imageDao = imageDao;
        this.authorizationService = authorizationService;
        this.directory = properties.directory().toAbsolutePath().normalize();
        this.maxFileSizeBytes = properties.maxFileSize().toBytes();
        this.thumbnailMaxEdge = properties.thumbnailMaxEdge();
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:upload') and @datasetImageService.canUpload(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetImage upload(String datasetId, String originalFilename, byte[] contents, String description, String relativePath) {
        return store(datasetId, originalFilename, contents, description, relativePath, false);
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:update') and @datasetImageService.canUpdate(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetImage replace(String datasetId, String originalFilename, byte[] contents, String description, String relativePath) {
        return store(datasetId, originalFilename, contents, description, relativePath, true);
    }

    private DatasetImage store(String datasetId, String originalFilename, byte[] contents, String description, String relativePath, boolean replaceExisting) {
        description = normalizeDescription(description);
        String filename = UploadFilename.resolve(originalFilename, relativePath);
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        if (imageDao.lockDataset(datasetId, actor) == null) {
            throw new AccessDeniedException("Dataset is unavailable or upload is not allowed.");
        }
        var previous = imageDao.findActiveByFilename(datasetId, filename);
        if (replaceExisting && previous == null) {
            authorizationService.hasPermission("dataset:image:upload");
        }
        if (previous != null && !replaceExisting) {
            throw new DuplicateKeyException("An image with this original filename already exists in the dataset.");
        }
        if (contents == null || contents.length == 0 || contents.length > maxFileSizeBytes) {
            throw new IllegalArgumentException("Image is empty or exceeds the configured maximum size.");
        }
        var decoded = validateImage(contents);
        String format = decoded.format();
        byte[] thumbnail = createThumbnail(decoded.image());
        String imageId = UUID.randomUUID().toString();
        String storagePath = imageId + "." + (format.equals("jpeg") ? "jpg" : format);
        String thumbnailPath = imageId + "-thumbnail.png";
        writeFile(directory.resolve(storagePath), contents);
        writeFile(directory.resolve(thumbnailPath), thumbnail);
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (previous != null) {
            var removed = previous.withDeletion(now, actor);
            if (!Integer.valueOf(1).equals(imageDao.markDeleted(removed))) {
                throw new AccessDeniedException("Dataset access changed before the image could be replaced.");
            }
            recordHistory("DELETE", removed, now, actor);
        }
        var image = new DatasetImage(imageId, datasetId, filename, storagePath, MEDIA_TYPES.get(format),
                Long.valueOf(contents.length), now, now, actor, actor, thumbnailPath, Boolean.FALSE,
                previous == null ? Integer.valueOf(1) : Integer.valueOf(Math.addExact(previous.versionCount(), 1)),
                previous == null ? null : previous.imageId(), null, null, null, null,
                previous == null ? now : previous.firstRegisteredAt(), description);
        if (!Integer.valueOf(1).equals(imageDao.insert(image))) {
            throw new AccessDeniedException("Dataset access changed before the image could be stored.");
        }
        recordHistory(previous == null ? "CREATE" : "UPDATE", image, now, actor);
        return imageDao.findById(datasetId, imageId);
    }

    private void recordHistory(String action, DatasetImage image, OffsetDateTime now, String actor) {
        if (!Integer.valueOf(1).equals(imageDao.insertHistory(new DatasetImageHistory(UUID.randomUUID().toString(), action, image, now, actor)))) {
            throw new IllegalStateException("Could not record image history.");
        }
    }

    private String normalizeDescription(String description) {
        if (description != null && description.length() > 2000) throw new IllegalArgumentException("Description must not exceed 2000 characters.");
        return description == null || description.isBlank() ? null : description;
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:update') and @datasetImageService.canUpdate(#datasetId)")
    @Transactional("appTransactionManager")
    public List<DatasetImage> updateDescriptions(String datasetId, List<ImageDescriptionChange> changes) {
        if (changes == null || changes.isEmpty() || changes.stream().map(ImageDescriptionChange::imageId).distinct().count() != changes.size()) {
            throw new IllegalArgumentException("Distinct image IDs are required.");
        }
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        if (imageDao.lockDataset(datasetId, actor) == null) throw new AccessDeniedException("Image update is not allowed.");
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var result = new ArrayList<DatasetImage>();
        for (var change : changes) {
            var image = imageDao.findById(datasetId, change.imageId());
            if (image == null) throw new NoSuchElementException("Image is unavailable.");
            var updated = image.withDescription(normalizeDescription(change.description()), now, actor);
            if (!Integer.valueOf(1).equals(imageDao.updateDescription(updated))) throw new AccessDeniedException("Image update is no longer allowed.");
            recordHistory("UPDATE", updated, now, actor);
            result.add(imageDao.findById(datasetId, image.imageId()));
        }
        return List.copyOf(result);
    }

    public boolean canRead(String datasetId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && Boolean.TRUE.equals(imageDao.canRead(datasetId, authentication.getName()));
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

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:delete') and @datasetImageService.canDelete(#datasetId)")
    @Transactional("appTransactionManager")
    public void delete(String datasetId, String imageId) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        if (imageDao.lockDataset(datasetId, actor) == null) {
            throw new AccessDeniedException("Dataset is unavailable or image deletion is not allowed.");
        }
        var image = imageDao.findById(datasetId, imageId);
        if (image == null) throw new NoSuchElementException("Image is unavailable.");
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var removed = image.withDeletion(now, actor);
        if (!Integer.valueOf(1).equals(imageDao.markDeleted(removed))) {
            throw new AccessDeniedException("Dataset access changed before the image could be deleted.");
        }
        recordHistory("DELETE", removed, now, actor);
    }

    private boolean canManage(String datasetId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && Boolean.TRUE.equals(imageDao.canManage(datasetId, authentication.getName()));
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<DatasetImageTarget> targets() {
        return imageDao.findTargets(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:read') and @datasetImageService.canRead(#datasetId)")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<DatasetImage> list(String datasetId) {
        return imageDao.findByDatasetId(datasetId);
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:read') and @datasetImageService.canRead(#datasetId)")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public byte[] thumbnail(String datasetId, String imageId) {
        var image = imageDao.findById(datasetId, imageId);
        if (image == null || image.thumbnailPath() == null) throw new NoSuchElementException("Thumbnail is unavailable.");
        return readFile(image.thumbnailPath());
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:read') and @datasetImageService.canRead(#datasetId)")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public DatasetImageContent content(String datasetId, String imageId) {
        var image = imageDao.findById(datasetId, imageId);
        if (image == null) throw new NoSuchElementException("Image is unavailable.");
        return new DatasetImageContent(readFile(image.storagePath()), image.mediaType());
    }

    private byte[] readFile(String storagePath) {
        Path file = directory.resolve(storagePath).normalize();
        if (!file.startsWith(directory)) throw new NoSuchElementException("Image file is unavailable.");
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException exception) {
            throw new NoSuchElementException("Image file is unavailable.");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the image file.", exception);
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
                                log.warn("Could not remove rolled-back image {}", file, exception);
                            }
                        }
                    }
                });
                output.write(contents);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store the image.", exception);
        }
    }

    private byte[] createThumbnail(BufferedImage image) {
        double scale = Math.min(1.0, thumbnailMaxEdge.doubleValue() / Math.max(image.getWidth(), image.getHeight()));
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        var thumbnail = new BufferedImage(width, height,
                image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        try {
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(thumbnail, "png", output)) throw new IOException("PNG encoder is unavailable.");
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not generate the thumbnail.", exception);
        }
    }

    private record DecodedImage(String format, BufferedImage image) {}

    private DecodedImage validateImage(byte[] contents) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(contents))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported or invalid image.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!MEDIA_TYPES.containsKey(format)) throw new IllegalArgumentException("Only PNG, JPEG, GIF and BMP are supported.");
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > 40_000_000) throw new IllegalArgumentException("More than 40 million pixels.");
                var image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Invalid image.");
                return new DecodedImage(format, image);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid image contents.", exception);
        }
    }
}
