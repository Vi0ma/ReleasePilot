package com.releasepilot.evaluation.domain;

public record EvaluationConfiguration(boolean enabled, int rolloutPercentage) {
    public EvaluationConfiguration {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("Rollout percentage must be between 0 and 100");
        }
    }
}
