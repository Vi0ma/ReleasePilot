package com.releasepilot.environment.controller;
import com.releasepilot.environment.dto.EnvironmentResponse;
import com.releasepilot.environment.service.EnvironmentService;
import com.releasepilot.security.UserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/environments")
public class EnvironmentController {
    private final EnvironmentService environments;
    public EnvironmentController(EnvironmentService environments) { this.environments = environments; }
    @GetMapping
    public List<EnvironmentResponse> list(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId) {
        return environments.list(actor.id(), projectId);
    }
    @GetMapping("/{environmentId}")
    public EnvironmentResponse get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId,
                                   @PathVariable Long environmentId) {
        return environments.get(actor.id(), projectId, environmentId);
    }
}
