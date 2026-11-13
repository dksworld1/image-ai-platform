package com.imageaiplatform.dataset.domain;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app.storage.files")
public record FileStorageProperties(Path directory, DataSize maxFileSize) {}
