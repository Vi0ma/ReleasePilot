package com.releasepilot.featureflag.service;
import com.releasepilot.audit.entity.AuditAction;
import com.releasepilot.audit.service.AuditService;
import com.releasepilot.environment.service.EnvironmentService;
import com.releasepilot.featureflag.dto.*;
import com.releasepilot.featureflag.entity.*;
import com.releasepilot.featureflag.repository.*;
import com.releasepilot.project.entity.Project;
import com.releasepilot.project.service.ProjectAccess;
import com.releasepilot.cache.CacheInvalidation;
import com.releasepilot.shared.error.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FeatureFlagService {
    private final FeatureFlagRepository flags;
    private final FlagConfigurationRepository configurations;
    private final EnvironmentService environments;
    private final ProjectAccess access;
    private final AuditService audit;
    private final CacheInvalidation invalidation;
    public FeatureFlagService(FeatureFlagRepository flags, FlagConfigurationRepository configurations,
                              EnvironmentService environments, ProjectAccess access, AuditService audit,
                              CacheInvalidation invalidation) {
        this.flags = flags; this.configurations = configurations; this.environments = environments;
        this.access = access; this.audit = audit;
        this.invalidation = invalidation;
    }
    @Transactional
    public FlagResponse create(Long actor, Long projectId, CreateFlagRequest request) {
        Project project = access.lockOwned(actor, projectId);
        if (flags.existsByProject_IdAndKey(projectId, request.key())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FLAG_KEY", "Flag key already exists in project");
        }
        project.advanceEvaluationRevision();
        var flag = flags.saveAndFlush(new FeatureFlag(project, request.key(), request.name(), request.description()));
        var defaults = environments.forProject(project).stream().map(env -> new FlagConfiguration(flag, env)).toList();
        configurations.saveAllAndFlush(defaults);
        audit.record(actor, project.getOwner().getId(), projectId, AuditAction.FLAG_CREATED, "FEATURE_FLAG",
                flag.getId(), Map.of("resource", snapshot(flag), "configurationCount", defaults.size()));
        return FlagResponse.from(flag);
    }
    @Transactional(readOnly = true)
    public List<FlagResponse> list(Long actor, Long projectId) {
        access.requireOwned(actor, projectId);
        return flags.findAllByProject_IdOrderByIdAsc(projectId).stream().map(FlagResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public FlagResponse get(Long actor, Long projectId, Long flagId) {
        access.requireOwned(actor, projectId);
        return FlagResponse.from(requireFlag(projectId, flagId));
    }
    @Transactional
    public FlagResponse update(Long actor, Long projectId, Long flagId, UpdateFlagRequest request) {
        Project project = access.lockOwned(actor, projectId);
        var flag = requireFlag(projectId, flagId);
        var before = snapshot(flag);
        flag.update(request.name(), request.description());
        flags.flush();
        audit.record(actor, project.getOwner().getId(), projectId, AuditAction.FLAG_UPDATED, "FEATURE_FLAG",
                flagId, Map.of("before", before, "after", snapshot(flag)));
        return FlagResponse.from(flag);
    }
    @Transactional
    public void delete(Long actor, Long projectId, Long flagId) {
        Project project = access.lockOwned(actor, projectId);
        var flag = requireFlag(projectId, flagId);
        var before = snapshot(flag);
        long previousRevision = project.getEvaluationRevision();
        var addresses = configurations.findCacheAddressesForFlag(flagId);
        project.advanceEvaluationRevision();
        configurations.deleteForFlag(flagId);
        flags.delete(flag);
        flags.flush();
        invalidation.afterCommit(previousRevision, addresses);
        audit.record(actor, project.getOwner().getId(), projectId, AuditAction.FLAG_DELETED, "FEATURE_FLAG",
                flagId, Map.of("resource", before));
    }
    @Transactional(readOnly = true)
    public List<ConfigurationResponse> configurations(Long actor, Long projectId, Long flagId) {
        access.requireOwned(actor, projectId);
        requireFlag(projectId, flagId);
        return configurations.findAllByFeatureFlag_IdOrderByEnvironment_IdAsc(flagId).stream()
                .map(ConfigurationResponse::from).toList();
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteForProject(Project ownedProject) {
        var addresses = configurations.findCacheAddressesForProject(ownedProject.getId());
        invalidation.afterCommit(ownedProject.getEvaluationRevision(), addresses);
        configurations.deleteForProject(ownedProject.getId());
        return flags.deleteForProject(ownedProject.getId());
    }
    private FeatureFlag requireFlag(Long projectId, Long flagId) {
        return flags.findByIdAndProject_Id(flagId, projectId).orElseThrow(ProjectAccess::notFound);
    }
    private Map<String, Object> snapshot(FeatureFlag flag) {
        return AuditService.snapshot(flag.getKey(), flag.getName(), flag.getDescription());
    }
}
