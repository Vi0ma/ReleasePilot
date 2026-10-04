package com.releasepilot.evaluation.service;

import com.releasepilot.cache.CacheKey;
import com.releasepilot.cache.ConfigurationCache;
import com.releasepilot.evaluation.domain.EvaluationReason;
import com.releasepilot.evaluation.domain.RolloutEngine;
import com.releasepilot.evaluation.dto.*;
import com.releasepilot.featureflag.repository.FlagConfigurationRepository;
import com.releasepilot.security.ApiKeyPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {
    private final FlagConfigurationRepository configurations;
    private final ConfigurationCache cache;
    private final RolloutEngine engine;
    public EvaluationService(FlagConfigurationRepository configurations, ConfigurationCache cache, RolloutEngine engine) {
        this.configurations = configurations;
        this.cache = cache;
        this.engine = engine;
    }
    @Transactional(readOnly = true)
    public EvaluationResponse evaluate(ApiKeyPrincipal scope, EvaluationRequest request) {
        var key = new CacheKey(scope.environmentId(), scope.evaluationRevision(), request.flagKey());
        var configuration = cache.get(key);
        if (configuration.isEmpty()) {
            configuration = configurations.findForEvaluation(scope.projectId(), scope.environmentId(), request.flagKey());
            if (configuration.isPresent()) { cache.put(key, configuration.get()); }
        }
        if (configuration.isEmpty()) { return new EvaluationResponse(request.flagKey(), false, EvaluationReason.FLAG_NOT_FOUND); }
        var decision = engine.evaluate(configuration.get(), request.userKey(), request.flagKey());
        return new EvaluationResponse(request.flagKey(), decision.enabled(), decision.reason());
    }
}
