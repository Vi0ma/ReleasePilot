package com.releasepilot.project.dto;
import jakarta.validation.constraints.*;

public record UpdateProjectRequest(@NotBlank @Size(max = 100) String name,
                                   @Size(max = 2000) String description) {
    public UpdateProjectRequest {
        name = name == null ? null : name.strip();
        description = description == null ? null : description.strip();
    }
}
