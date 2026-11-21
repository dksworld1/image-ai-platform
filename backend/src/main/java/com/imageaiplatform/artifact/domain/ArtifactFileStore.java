package com.imageaiplatform.artifact.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Local storage only; database locations contain a configured root key, never an absolute path. */
@Component
@EnableConfigurationProperties(ArtifactStorageProperties.class)
public class ArtifactFileStore {
    private final ArtifactStorageProperties properties;
    public ArtifactFileStore(ArtifactStorageProperties properties) { this.properties = properties; }
    public String defaultRoot() { return properties.defaultRoot(); }
    public record Facts(Long sizeBytes, String checksum) {}
    private Path path(String rootKey, String relativePath, boolean create) throws IOException {
        var configured = properties.roots().get(rootKey);
        if (configured == null) throw new IllegalStateException("Artifact root is not configured: " + rootKey);
        if (!relativePath.matches("[a-zA-Z0-9-]+/[a-zA-Z0-9-]+")) throw new IllegalArgumentException("Invalid storage path.");
        Path root = configured.toAbsolutePath().normalize();
        if (create) Files.createDirectories(root);
        root = root.toRealPath();
        Path parent = root.resolve(relativePath.substring(0, relativePath.indexOf('/')));
        if (Files.isSymbolicLink(parent)) throw new IllegalArgumentException("Symbolic link storage is not allowed.");
        if (create) Files.createDirectories(parent);
        Path target = root.resolve(relativePath);
        if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("Symbolic link storage is not allowed.");
        return target;
    }
    private MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public Facts write(String root, String relativePath, InputStream input, long declaredSize) {
        if (declaredSize < 0 || declaredSize > properties.maxFileSize().toBytes()) throw new IllegalArgumentException("Invalid file size.");
        try (var output = Files.newOutputStream(path(root, relativePath, true), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            var hash = digest(); byte[] buffer = new byte[8192]; long size = 0; int count;
            while ((count = input.read(buffer)) != -1) {
                size = Math.addExact(size, count);
                if (size > properties.maxFileSize().toBytes()) throw new IllegalArgumentException("File exceeds configured maximum size.");
                hash.update(buffer, 0, count); output.write(buffer, 0, count);
            }
            if (size != declaredSize) throw new IllegalArgumentException("Declared file size does not match contents.");
            return new Facts(Long.valueOf(size), HexFormat.of().formatHex(hash.digest()));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    public InputStream open(ArtifactFile file) {
        try { return Files.newInputStream(path(file.rootKey(), file.relativePath(), false), LinkOption.NOFOLLOW_LINKS); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    public void verify(ArtifactFile file) {
        try (var input = open(file)) {
            var hash = digest(); byte[] buffer = new byte[8192]; long size = 0; int count;
            while ((count = input.read(buffer)) != -1) { size += count; hash.update(buffer, 0, count); }
            if (size != file.sizeBytes() || !HexFormat.of().formatHex(hash.digest()).equals(file.checksum())) {
                throw new ArtifactConflictException("Artifact file size or checksum does not match.");
            }
        } catch (IOException | UncheckedIOException exception) { throw new ArtifactConflictException("Artifact file is unavailable."); }
    }
    public void remove(String root, String relativePath) {
        try { Files.deleteIfExists(path(root, relativePath, false)); }
        catch (java.nio.file.NoSuchFileException ignored) { /* A failed write may not have created the object. */ }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
}
