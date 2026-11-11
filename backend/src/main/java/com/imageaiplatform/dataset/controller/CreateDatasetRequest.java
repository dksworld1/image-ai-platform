package com.imageaiplatform.dataset.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateDatasetRequest(@NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotEmpty List<@NotBlank @Size(max = 36) String> workspaceIds) {}
