package com.imageaiplatform.storage.domain;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app.storage.artifacts")
public record ArtifactStorageProperties(String defaultRoot, Map<String, Path> roots, DataSize maxFileSize) {}
