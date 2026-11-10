package com.imageaiplatform.dataset.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDatasetRequest(@NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description) {}
