package com.releasepilot.evaluation;

import com.releasepilot.cache.*;
import com.releasepilot.evaluation.domain.*;
import com.releasepilot.evaluation.dto.*;
import com.releasepilot.evaluation.service.EvaluationService;
import com.releasepilot.featureflag.repository.FlagConfigurationRepository;
import com.releasepilot.security.ApiKeyPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvaluationServiceTest {
    private final FlagConfigurationRepository configurations = mock(FlagConfigurationRepository.class);
    private final ConfigurationCache cache = mock(ConfigurationCache.class);
    private final EvaluationService service = new EvaluationService(configurations, cache, new RolloutEngine(new StableBucketHasher()));
    private final ApiKeyPrincipal scope = new ApiKeyPrincipal(2L, 1L, 3);
    private final EvaluationRequest request = new EvaluationRequest("checkout", "user");
    private final CacheKey key = new CacheKey(2L, 3, "checkout");
    @Test void cacheHitSkipsConfigurationQuery() {
        when(cache.get(key)).thenReturn(Optional.of(new EvaluationConfiguration(true, 100)));
        assertThat(service.evaluate(scope, request).enabled()).isTrue();
        verifyNoInteractions(configurations);
        verify(cache, never()).put(any(), any());
    }
    @Test void missLoadsPostgresqlAndPopulatesCache() {
        var configuration = new EvaluationConfiguration(false, 0);
        when(cache.get(key)).thenReturn(Optional.empty());
        when(configurations.findForEvaluation(1L, 2L, "checkout")).thenReturn(Optional.of(configuration));
        assertThat(service.evaluate(scope, request).reason()).isEqualTo(EvaluationReason.FLAG_DISABLED);
        verify(cache).put(key, configuration);
    }
    @Test void missingFlagIsDisabledAndNotNegativelyCached() {
        when(cache.get(key)).thenReturn(Optional.empty());
        when(configurations.findForEvaluation(1L, 2L, "checkout")).thenReturn(Optional.empty());
        assertThat(service.evaluate(scope, request)).isEqualTo(new EvaluationResponse("checkout", false, EvaluationReason.FLAG_NOT_FOUND));
        verify(cache, never()).put(any(), any());
    }
}
