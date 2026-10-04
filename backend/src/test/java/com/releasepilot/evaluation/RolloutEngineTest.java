package com.releasepilot.evaluation;

import com.releasepilot.evaluation.domain.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RolloutEngineTest {
    private final StableBucketHasher hasher = new StableBucketHasher();
    private final RolloutEngine engine = new RolloutEngine(hasher);
    @Test void sha256VectorsFixTheAlgorithmAcrossRestarts() {
        assertThat(hasher.bucket("user-123", "new_checkout")).isEqualTo(60);
        assertThat(hasher.bucket("alice", "dark_mode")).isEqualTo(97);
        assertThat(hasher.bucket("", "flag")).isEqualTo(19);
        for (int i = 0; i < 100; i++) {
            assertThat(new StableBucketHasher().bucket("user-123", "new_checkout")).isEqualTo(60);
        }
    }
    @Test void bucketsAreBoundedDistributedAndFlagSpecific() {
        var buckets = new HashSet<Integer>();
        int different = 0;
        for (int i = 0; i < 5000; i++) {
            int bucket = hasher.bucket("user-" + i, "checkout");
            assertThat(bucket).isBetween(0, 99);
            buckets.add(bucket);
            if (bucket != hasher.bucket("user-" + i, "dark_mode")) { different++; }
        }
        assertThat(buckets).hasSize(100);
        assertThat(different).isGreaterThan(4500);
    }
    @Test void disabledOverridesEveryPercentage() {
        for (int percentage : new int[]{0, 1, 50, 100}) {
            assertThat(engine.evaluate(new EvaluationConfiguration(false, percentage), "user", "flag"))
                    .isEqualTo(new EvaluationDecision(false, EvaluationReason.FLAG_DISABLED));
        }
    }
    @Test void zeroAndHundredAreExactForAllUsers() {
        for (int i = 0; i < 1000; i++) {
            assertThat(engine.evaluate(new EvaluationConfiguration(true, 0), "u" + i, "f"))
                    .isEqualTo(new EvaluationDecision(false, EvaluationReason.ROLLOUT_MISS));
            assertThat(engine.evaluate(new EvaluationConfiguration(true, 100), "u" + i, "f"))
                    .isEqualTo(new EvaluationDecision(true, EvaluationReason.ROLLOUT_MATCH));
        }
    }
    @Test void thresholdIsStrictAndIncreasingRolloutNeverRemovesUsers() {
        assertThat(engine.evaluate(new EvaluationConfiguration(true, 60), "user-123", "new_checkout").enabled()).isFalse();
        assertThat(engine.evaluate(new EvaluationConfiguration(true, 61), "user-123", "new_checkout").enabled()).isTrue();
        for (int i = 0; i < 100; i++) {
            int bucket = hasher.bucket("u" + i, "f");
            for (int percentage = 1; percentage < 100; percentage++) {
                var decision = engine.evaluate(new EvaluationConfiguration(true, percentage), "u" + i, "f");
                assertThat(decision.enabled()).isEqualTo(bucket < percentage);
                assertThat(decision.reason()).isEqualTo(bucket < percentage ? EvaluationReason.ROLLOUT_MATCH : EvaluationReason.ROLLOUT_MISS);
            }
        }
    }
    @Test void invalidPersistedPercentagesCannotBeEvaluated() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationConfiguration(true, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationConfiguration(true, 101));
    }
}
