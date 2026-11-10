package com.imageaiplatform.auth.controller;

public record TokenResponse(String accessToken, String tokenType, Long expiresIn,
        String userKey, String userId, String username) {}
