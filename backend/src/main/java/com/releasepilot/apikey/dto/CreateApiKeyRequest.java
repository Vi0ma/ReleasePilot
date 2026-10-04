package com.releasepilot.apikey.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name) {
    public CreateApiKeyRequest { name = name == null ? null : name.strip(); }
}
