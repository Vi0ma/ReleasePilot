package com.releasepilot.apikey.service;

import com.releasepilot.apikey.repository.ApiKeyRepository;
import com.releasepilot.security.ApiKeyPrincipal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyAuthenticationService {
    private final ApiKeyRepository keys;
    private final ApiKeyCodec codec;
    public ApiKeyAuthenticationService(ApiKeyRepository keys, ApiKeyCodec codec) {
        this.keys = keys;
        this.codec = codec;
    }
    @Transactional(readOnly = true)
    public Optional<ApiKeyPrincipal> authenticate(String raw) {
        var parsed = codec.parse(raw);
        if (parsed.isEmpty()) { return Optional.empty(); }
        return keys.findForAuthentication(parsed.get().publicId())
                .filter(key -> key.getRevokedAt() == null)
                .filter(key -> codec.matches(parsed.get().secret(), key.getSecretHash()))
                .map(key -> new ApiKeyPrincipal(key.getEnvironment().getId(),
                        key.getEnvironment().getProject().getId(),
                        key.getEnvironment().getProject().getEvaluationRevision()));
    }
}
