package com.releasepilot.audit.controller;
import com.releasepilot.audit.dto.AuditPage;
import com.releasepilot.audit.service.AuditService;
import com.releasepilot.security.UserPrincipal;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/audit")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }
    @GetMapping
    public AuditPage list(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId,
                         @RequestParam(defaultValue = "0") @Min(0) int page,
                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return audit.list(actor.id(), projectId, page, size);
    }
}
