package com.releasepilot.featureflag.dto;
import jakarta.validation.constraints.*;

public record CreateFlagRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9_-]+") String key,
        @Size(max = 2000) String description) {
    public CreateFlagRequest {
        name = name == null ? null : name.strip();
        key = key == null ? null : key.strip();
        description = description == null ? null : description.strip();
    }
}
