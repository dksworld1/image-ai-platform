package com.imageaiplatform.storage.domain;

import java.io.InputStream;

public interface ArtifactStorage {
    record Facts(Long sizeBytes, String checksum) {}

    String defaultRoot();

    Facts write(String rootKey, String relativePath, InputStream input, long declaredSize);

    InputStream open(String rootKey, String relativePath);

    Facts inspect(String rootKey, String relativePath);

    void remove(String rootKey, String relativePath);
}
