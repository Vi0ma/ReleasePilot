package com.releasepilot.featureflag.controller;
import com.releasepilot.featureflag.dto.*;
import com.releasepilot.featureflag.service.FeatureFlagService;
import java.util.List;
import com.releasepilot.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/flags")
public class FeatureFlagController {
    private final FeatureFlagService flags;
    public FeatureFlagController(FeatureFlagService flags) { this.flags = flags; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FlagResponse create(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId,
                               @Valid @RequestBody CreateFlagRequest request) {
        return flags.create(actor.id(), projectId, request);
    }
    @GetMapping
    public List<FlagResponse> list(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId) {
        return flags.list(actor.id(), projectId);
    }
    @GetMapping("/{flagId}")
    public FlagResponse get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId, @PathVariable Long flagId) {
        return flags.get(actor.id(), projectId, flagId);
    }
    @PutMapping("/{flagId}")
    public FlagResponse update(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId,
                               @PathVariable Long flagId, @Valid @RequestBody UpdateFlagRequest request) {
        return flags.update(actor.id(), projectId, flagId, request);
    }
    @DeleteMapping("/{flagId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId, @PathVariable Long flagId) {
        flags.delete(actor.id(), projectId, flagId);
    }
    @GetMapping("/{flagId}/configurations")
    public List<ConfigurationResponse> configurations(@AuthenticationPrincipal UserPrincipal actor,
                                                      @PathVariable Long projectId, @PathVariable Long flagId) {
        return flags.configurations(actor.id(), projectId, flagId);
    }
}
