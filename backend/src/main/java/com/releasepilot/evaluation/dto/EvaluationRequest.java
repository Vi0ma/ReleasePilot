package com.releasepilot.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

public record EvaluationRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9_-]+") String flagKey,
        @NotBlank @Size(max = 256) String userKey) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected evaluation field");
    }
}
