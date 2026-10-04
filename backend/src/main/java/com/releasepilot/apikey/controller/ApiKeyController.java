package com.releasepilot.apikey.controller;

import com.releasepilot.apikey.dto.*;
import com.releasepilot.apikey.service.ApiKeyService;
import com.releasepilot.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/environments/{environmentId}/api-keys")
public class ApiKeyController {
    private final ApiKeyService keys;
    public ApiKeyController(ApiKeyService keys) { this.keys = keys; }
    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> create(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable Long projectId, @PathVariable Long environmentId, @Valid @RequestBody CreateApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(keys.create(actor.id(), projectId, environmentId, request));
    }
    @GetMapping
    public List<ApiKeyResponse> list(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId,
                                    @PathVariable Long environmentId) {
        return keys.list(actor.id(), projectId, environmentId);
    }
    @DeleteMapping("/{keyId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId,
                       @PathVariable Long environmentId, @PathVariable Long keyId) {
        keys.revoke(actor.id(), projectId, environmentId, keyId);
    }
}
