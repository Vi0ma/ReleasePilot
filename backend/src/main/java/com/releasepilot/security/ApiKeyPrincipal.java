package com.releasepilot.security;

public record ApiKeyPrincipal(Long environmentId, Long projectId, long evaluationRevision) { }
