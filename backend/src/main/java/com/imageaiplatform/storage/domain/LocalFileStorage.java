package com.imageaiplatform.storage.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ArtifactStorageProperties.class)
public class LocalFileStorage implements ArtifactStorage {
    private final ArtifactStorageProperties properties;

    public LocalFileStorage(ArtifactStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String defaultRoot() {
        return properties.defaultRoot();
    }

    @Override
    public Facts write(String rootKey, String relativePath, InputStream input, long declaredSize) {
        try {
            long maxSize = properties.maxFileSize().toBytes();
            if (declaredSize < 0 || declaredSize > maxSize) {
                throw new IllegalArgumentException("Invalid file size.");
            }
            return writeFile(resolve(rootKey, relativePath, true), input, declaredSize, maxSize);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private Facts writeFile(Path target, InputStream input, long declaredSize, long maxSize) throws IOException {
        var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try (output) {
            Facts facts = measure(input, output, maxSize);
            if (facts.sizeBytes().longValue() != declaredSize) {
                throw new IllegalArgumentException("Declared file size does not match contents.");
            }
            return facts;
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException | RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public InputStream open(String rootKey, String relativePath) {
        try {
            return Files.newInputStream(resolve(rootKey, relativePath, false), LinkOption.NOFOLLOW_LINKS);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public Facts inspect(String rootKey, String relativePath) {
        try (var input = open(rootKey, relativePath)) {
            return measure(input, OutputStream.nullOutputStream(), Long.MAX_VALUE);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public void remove(String rootKey, String relativePath) {
        try {
            Files.deleteIfExists(resolve(rootKey, relativePath, false));
        } catch (NoSuchFileException ignored) {
            // The root or object may already be absent.
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private Path resolve(String rootKey, String relativePath, boolean create) throws IOException {
        Path configuredRoot = properties.roots().get(rootKey);
        if (configuredRoot == null) {
            throw new IllegalStateException("Artifact root is not configured: " + rootKey);
        }
        if (!relativePath.matches("[a-zA-Z0-9-]+/[a-zA-Z0-9-]+")) {
            throw new IllegalArgumentException("Invalid storage path.");
        }
        Path root = configuredRoot.toAbsolutePath().normalize();
        if (create) Files.createDirectories(root);
        root = root.toRealPath();
        Path target = root.resolve(relativePath);
        Path parent = target.getParent();
        if (Files.isSymbolicLink(parent)) {
            throw new IllegalArgumentException("Symbolic link storage is not allowed.");
        }
        if (create) Files.createDirectories(parent);
        if (Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("Symbolic link storage is not allowed.");
        }
        return target;
    }

    private Facts measure(InputStream input, OutputStream output, long maxSize) throws IOException {
        MessageDigest hash;
        try {
            hash = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
        byte[] buffer = new byte[8192];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            size = Math.addExact(size, count);
            if (size > maxSize) throw new IllegalArgumentException("File exceeds configured maximum size.");
            hash.update(buffer, 0, count);
            output.write(buffer, 0, count);
        }
        return new Facts(size, HexFormat.of().formatHex(hash.digest()));
    }
}
