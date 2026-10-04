package com.releasepilot.featureflag.dto;
import jakarta.validation.constraints.*;

public record UpdateFlagRequest(@NotBlank @Size(max = 100) String name,
                                   @Size(max = 2000) String description) {
    public UpdateFlagRequest {
        name = name == null ? null : name.strip();
        description = description == null ? null : description.strip();
    }
}
