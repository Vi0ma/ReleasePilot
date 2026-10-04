package com.releasepilot.featureflag.dto;
import jakarta.validation.constraints.*;

public record UpdateConfigurationRequest(@NotNull Boolean enabled,
                                          @NotNull @Min(0) @Max(100) Integer rolloutPercentage) { }
