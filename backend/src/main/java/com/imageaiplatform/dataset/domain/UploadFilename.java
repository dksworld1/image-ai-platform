package com.imageaiplatform.dataset.domain;

public final class UploadFilename {
    private UploadFilename() {}

    public static String resolve(String originalFilename, String relativePath) {
        String name = relativePath != null ? relativePath : originalFilename;
        name = name == null ? "" : name.replace('\\', '/');
        if (relativePath == null) name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 1024 || name.indexOf(':') >= 0
                || name.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new IllegalArgumentException("Original relative path must contain 1 to 1024 characters.");
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isBlank() || segment.length() > 255 || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Original relative path contains an invalid segment.");
            }
        }
        return name;
    }
}
