package com.releasepilot.evaluation.domain;

import org.springframework.stereotype.Component;

@Component
public class RolloutEngine {
    private final StableBucketHasher hasher;
    public RolloutEngine(StableBucketHasher hasher) { this.hasher = hasher; }
    public EvaluationDecision evaluate(EvaluationConfiguration configuration, String userKey, String flagKey) {
        if (!configuration.enabled()) { return new EvaluationDecision(false, EvaluationReason.FLAG_DISABLED); }
        boolean match = configuration.rolloutPercentage() == 100
                || (configuration.rolloutPercentage() != 0
                    && hasher.bucket(userKey, flagKey) < configuration.rolloutPercentage());
        return new EvaluationDecision(match, match ? EvaluationReason.ROLLOUT_MATCH : EvaluationReason.ROLLOUT_MISS);
    }
}
