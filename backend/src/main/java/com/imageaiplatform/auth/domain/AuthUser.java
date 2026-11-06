package com.imageaiplatform.auth.domain;

public record AuthUser(String username, String passwordHash, Boolean enabled) {}
