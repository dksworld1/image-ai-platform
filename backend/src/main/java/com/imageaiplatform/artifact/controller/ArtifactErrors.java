package com.imageaiplatform.artifact.controller;

import com.imageaiplatform.artifact.domain.ArtifactConflictException;
import java.util.NoSuchElementException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ArtifactController.class, ArtifactSetController.class})
public class ArtifactErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> invalid() { return ResponseEntity.badRequest().build(); }
    @ExceptionHandler({ArtifactConflictException.class, DuplicateKeyException.class})
    public ResponseEntity<Void> conflict() { return ResponseEntity.status(HttpStatus.CONFLICT).build(); }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> missing() { return ResponseEntity.notFound().build(); }
}
