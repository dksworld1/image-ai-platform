package com.imageaiplatform.dataset.service;

import com.imageaiplatform.dataset.dao.DatasetImageDao;
import com.imageaiplatform.dataset.domain.DatasetImage;
import com.imageaiplatform.dataset.domain.ImageStorageProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
    private final DatasetService datasetService;
    private final Path directory;
    private final long maxFileSizeBytes;

    public DatasetImageService(DatasetImageDao imageDao, DatasetService datasetService, ImageStorageProperties properties) {
        this.imageDao = imageDao;
        this.datasetService = datasetService;
        this.directory = properties.directory().toAbsolutePath().normalize();
        this.maxFileSizeBytes = properties.maxFileSize().toBytes();
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:image:upload') and @datasetService.canManage(#datasetId)")
    @Transactional("appTransactionManager")
    public DatasetImage upload(String datasetId, String originalFilename, byte[] contents) {
        String filename = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.length() > 255 || filename.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Original filename must contain 1 to 255 characters.");
        }
        if (Boolean.TRUE.equals(imageDao.existsByOriginalFilename(datasetId, filename))) {
            throw new DuplicateKeyException("An image with this original filename already exists in the dataset.");
        }
        if (contents == null || contents.length == 0 || contents.length > maxFileSizeBytes) {
            throw new IllegalArgumentException("Image is empty or exceeds the configured maximum size.");
        }
        String format = validateImage(contents);
        String imageId = UUID.randomUUID().toString();
        String storagePath = imageId + "." + (format.equals("jpeg") ? "jpg" : format);
        Path file = directory.resolve(storagePath);
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
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var image = new DatasetImage(imageId, datasetId, filename, storagePath, MEDIA_TYPES.get(format),
                Long.valueOf(contents.length), now, now, actor, actor);
        if (!Integer.valueOf(1).equals(imageDao.insert(image))) {
            throw new AccessDeniedException("Dataset access changed before the image could be stored.");
        }
        return image;
    }

    @PreAuthorize("@authorizationService.hasPermission('dataset:item:read')")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<DatasetImage> list(String datasetId) {
        datasetService.detail(datasetId);
        return imageDao.findByDatasetId(datasetId);
    }

    private String validateImage(byte[] contents) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(contents))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported or invalid image.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!MEDIA_TYPES.containsKey(format)) throw new IllegalArgumentException("Only PNG, JPEG, GIF and BMP are supported.");
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > 40_000_000 || reader.read(0) == null) throw new IllegalArgumentException("Invalid image or more than 40 million pixels.");
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid image contents.", exception);
        }
    }
}
