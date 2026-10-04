package com.releasepilot.featureflag.controller;
import com.releasepilot.featureflag.dto.*;
import com.releasepilot.featureflag.service.FlagConfigurationService;
import com.releasepilot.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flags/{flagId}/environments/{environmentId}")
public class FlagConfigurationController {
    private final FlagConfigurationService configurations;
    public FlagConfigurationController(FlagConfigurationService configurations) { this.configurations = configurations; }
    @GetMapping
    public ConfigurationResponse get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long flagId,
                                     @PathVariable Long environmentId) {
        return configurations.get(actor.id(), flagId, environmentId);
    }
    @PatchMapping
    public ConfigurationResponse update(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long flagId,
                                        @PathVariable Long environmentId,
                                        @Valid @RequestBody UpdateConfigurationRequest request) {
        return configurations.update(actor.id(), flagId, environmentId, request);
    }
}
