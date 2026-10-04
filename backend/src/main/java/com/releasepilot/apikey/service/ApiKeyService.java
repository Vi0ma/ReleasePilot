package com.releasepilot.apikey.service;

import com.releasepilot.apikey.dto.*;
import com.releasepilot.apikey.entity.ApiKey;
import com.releasepilot.apikey.repository.ApiKeyRepository;
import com.releasepilot.audit.entity.AuditAction;
import com.releasepilot.audit.service.AuditService;
import com.releasepilot.environment.entity.Environment;
import com.releasepilot.environment.repository.EnvironmentRepository;
import com.releasepilot.project.entity.Project;
import com.releasepilot.project.service.ProjectAccess;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ApiKeyService {
    private final ApiKeyRepository keys;
    private final EnvironmentRepository environments;
    private final ProjectAccess access;
    private final ApiKeyCodec codec;
    private final AuditService audit;
    public ApiKeyService(ApiKeyRepository keys, EnvironmentRepository environments, ProjectAccess access,
                         ApiKeyCodec codec, AuditService audit) {
        this.keys = keys;
        this.environments = environments;
        this.access = access;
        this.codec = codec;
        this.audit = audit;
    }
    @Transactional
    public ApiKeyCreatedResponse create(Long actor, Long projectId, Long environmentId, CreateApiKeyRequest request) {
        access.lockOwned(actor, projectId);
        var environment = requireEnvironment(projectId, environmentId);
        var generated = codec.generate();
        var key = keys.saveAndFlush(new ApiKey(environment, request.name(), generated.publicId(), generated.secretHash()));
        audit.record(actor, actor, projectId, AuditAction.API_KEY_CREATED, "API_KEY", key.getId(),
                Map.of("environmentId", environmentId, "name", key.getName(), "prefix", "rp_" + key.getPublicId()));
        return new ApiKeyCreatedResponse(ApiKeyResponse.from(key), generated.rawKey());
    }
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(Long actor, Long projectId, Long environmentId) {
        access.requireOwned(actor, projectId);
        requireEnvironment(projectId, environmentId);
        return keys.findAllByEnvironment_IdOrderByIdAsc(environmentId).stream().map(ApiKeyResponse::from).toList();
    }
    @Transactional
    public void revoke(Long actor, Long projectId, Long environmentId, Long keyId) {
        access.lockOwned(actor, projectId);
        requireEnvironment(projectId, environmentId);
        var key = keys.findByIdAndEnvironment_Id(keyId, environmentId).orElseThrow(ProjectAccess::notFound);
        if (key.getRevokedAt() != null) { return; }
        key.revoke();
        keys.flush();
        audit.record(actor, actor, projectId, AuditAction.API_KEY_REVOKED, "API_KEY", keyId,
                Map.of("environmentId", environmentId, "prefix", "rp_" + key.getPublicId()));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteForProject(Project ownedProject) { return keys.deleteForProject(ownedProject.getId()); }
    private Environment requireEnvironment(Long project, Long environment) {
        return environments.findByIdAndProject_Id(environment, project).orElseThrow(ProjectAccess::notFound);
    }
}
