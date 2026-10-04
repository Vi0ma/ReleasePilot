package com.releasepilot.featureflag.service;
import com.releasepilot.audit.entity.AuditAction;
import com.releasepilot.audit.service.AuditService;
import com.releasepilot.featureflag.dto.*;
import com.releasepilot.featureflag.entity.*;
import com.releasepilot.featureflag.repository.*;
import com.releasepilot.project.service.ProjectAccess;
import com.releasepilot.cache.CacheAddress;
import com.releasepilot.cache.CacheInvalidation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlagConfigurationService {
    private final FeatureFlagRepository flags;
    private final FlagConfigurationRepository configurations;
    private final ProjectAccess access;
    private final AuditService audit;
    private final CacheInvalidation invalidation;
    public FlagConfigurationService(FeatureFlagRepository flags, FlagConfigurationRepository configurations,
                                    ProjectAccess access, AuditService audit, CacheInvalidation invalidation) {
        this.flags = flags; this.configurations = configurations; this.access = access; this.audit = audit;
        this.invalidation = invalidation;
    }
    @Transactional(readOnly = true)
    public ConfigurationResponse get(Long actor, Long flagId, Long environmentId) {
        var flag = requireFlag(flagId);
        access.requireOwned(actor, flag.getProject().getId());
        return ConfigurationResponse.from(requireConfiguration(flagId, environmentId));
    }
    @Transactional
    public ConfigurationResponse update(Long actor, Long flagId, Long environmentId, UpdateConfigurationRequest request) {
        var flag = requireFlag(flagId);
        var project = access.lockOwned(actor, flag.getProject().getId());
        // Re-query after the project lock: a concurrent delete may have completed before the lock.
        flag = flags.findByIdAndProject_Id(flagId, project.getId()).orElseThrow(ProjectAccess::notFound);
        var configuration = requireConfiguration(flagId, environmentId);
        var before = state(configuration);
        long previousRevision = project.getEvaluationRevision();
        project.advanceEvaluationRevision();
        configuration.update(request.enabled(), request.rolloutPercentage());
        configurations.flush();
        invalidation.afterCommit(previousRevision, List.of(new CacheAddress(environmentId, flag.getKey())));
        audit.record(actor, project.getOwner().getId(), project.getId(), AuditAction.FLAG_CONFIGURATION_UPDATED,
                "FLAG_CONFIGURATION", configuration.getId(), Map.of(
                        "flagId", flag.getId(), "environmentId", environmentId,
                        "before", before, "after", state(configuration)));
        return ConfigurationResponse.from(configuration);
    }
    private FeatureFlag requireFlag(Long flagId) {
        return flags.findById(flagId).orElseThrow(ProjectAccess::notFound);
    }
    private FlagConfiguration requireConfiguration(Long flagId, Long environmentId) {
        return configurations.findByFeatureFlag_IdAndEnvironment_Id(flagId, environmentId)
                .orElseThrow(ProjectAccess::notFound);
    }
    private Map<String, Object> state(FlagConfiguration configuration) {
        return Map.of("enabled", configuration.isEnabled(), "rolloutPercentage", configuration.getRolloutPercentage());
    }
}
