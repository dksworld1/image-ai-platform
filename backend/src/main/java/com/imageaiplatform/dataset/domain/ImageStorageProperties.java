package com.imageaiplatform.dataset.domain;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app.storage.images")
public record ImageStorageProperties(Path directory, DataSize maxFileSize, Integer thumbnailMaxEdge) {}
