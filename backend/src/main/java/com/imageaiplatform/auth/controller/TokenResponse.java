package com.imageaiplatform.auth.controller;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
