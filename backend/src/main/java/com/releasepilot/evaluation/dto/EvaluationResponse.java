package com.releasepilot.evaluation.dto;

import com.releasepilot.evaluation.domain.EvaluationReason;

public record EvaluationResponse(String flagKey, boolean enabled, EvaluationReason reason) { }
