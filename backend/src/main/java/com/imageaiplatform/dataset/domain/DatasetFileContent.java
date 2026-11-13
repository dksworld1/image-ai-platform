package com.imageaiplatform.dataset.domain;

public record DatasetFileContent(byte[] contents, String mediaType, String originalFilename) {}
