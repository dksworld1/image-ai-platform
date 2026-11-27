package com.imageaiplatform.dataset.domain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DatasetStorageDirectory(Path root, String pattern, Path legacyRoot) {
    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]*)\\}|[{}]");

    public static DatasetStorageDirectory from(Path directory, String pattern, Path legacyDirectory) {
        if (TOKEN.matcher(directory.toString()).find()
                || (legacyDirectory != null && TOKEN.matcher(legacyDirectory.toString()).find())) {
            throw new IllegalArgumentException("Dataset storage roots cannot contain tokens.");
        }
        Path relative = Path.of(expand(pattern, tokens("dataset-id")));
        if (relative.getRoot() != null || pattern.isBlank()) {
            throw new IllegalArgumentException("Dataset directory-pattern must be a relative path.");
        }
        return new DatasetStorageDirectory(canonical(directory), pattern,
                legacyDirectory == null ? null : canonical(legacyDirectory));
    }

    public Path fileForWrite(String datasetId, String filename) {
        if (!datasetId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("Dataset ID cannot be used as a storage directory name.");
        }
        Path file = canonical(root.resolve(expand(pattern, tokens(datasetId))).resolve(filename));
        if (!file.startsWith(root) || file.equals(root)) {
            throw new IllegalStateException("Dataset file must be inside its storage root.");
        }
        return file;
    }

    public String relativePath(Path file) {
        return root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
    }

    private static Path canonical(Path path) {
        try {
            return path.toFile().getCanonicalFile().toPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not resolve dataset storage path.", exception);
        }
    }

    private static Map<String, String> tokens(String datasetId) {
        return Map.of("dataset_id", datasetId);
    }

    static String expand(String pattern, Map<String, String> tokens) {
        return TOKEN.matcher(pattern).replaceAll(match -> {
            String name = match.group(1);
            String value = name == null ? null : tokens.get(name);
            if (value == null) {
                throw new IllegalArgumentException("Invalid dataset directory-pattern token: " + match.group());
            }
            return Matcher.quoteReplacement(value);
        });
    }

    public Path existingFile(String storagePath) throws IOException {
        Path relative = Path.of(storagePath).normalize();
        if (relative.getRoot() != null || relative.startsWith("..")) {
            throw new NoSuchFileException(storagePath);
        }
        try {
            return existingFile(root, relative);
        } catch (NoSuchFileException exception) {
            if (legacyRoot == null) throw exception;
            return existingFile(legacyRoot, relative);
        }
    }

    private static Path existingFile(Path directory, Path relative) throws IOException {
        Path realRoot = directory.toRealPath();
        Path file = realRoot.resolve(relative).toRealPath();
        if (!file.startsWith(realRoot) || !Files.isRegularFile(file)) {
            throw new NoSuchFileException(relative.toString());
        }
        return file;
    }
}
