package com.imageaiplatform.auth.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 100) String userId, @NotBlank String password) {}
