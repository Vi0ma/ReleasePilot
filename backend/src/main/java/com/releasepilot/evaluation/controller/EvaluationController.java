package com.releasepilot.evaluation.controller;

import com.releasepilot.evaluation.dto.*;
import com.releasepilot.evaluation.service.EvaluationService;
import com.releasepilot.security.ApiKeyPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evaluate")
public class EvaluationController {
    private final EvaluationService evaluation;
    public EvaluationController(EvaluationService evaluation) { this.evaluation = evaluation; }
    @PostMapping
    public EvaluationResponse evaluate(@AuthenticationPrincipal ApiKeyPrincipal scope,
                                       @Valid @RequestBody EvaluationRequest request) {
        return evaluation.evaluate(scope, request);
    }
}
