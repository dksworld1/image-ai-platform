package com.imageaiplatform.dataset.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateFileDescriptionsRequest(@NotEmpty List<@NotNull @Valid Item> items) {
    public record Item(@NotBlank @Size(max = 36) String fileId, @Size(max = 2000) String description) {}
}
