package com.releasepilot.apikey;

import com.releasepilot.apikey.service.ApiKeyCodec;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApiKeyCodecTest {
    private final ApiKeyCodec codec = new ApiKeyCodec();
    @Test void generatesDistinctKeysWithPublicIdentifierAndHashedSecret() {
        var keys = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            var generated = codec.generate();
            assertThat(generated.rawKey()).matches("rp_[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}");
            var parsed = codec.parse(generated.rawKey()).orElseThrow();
            assertThat(parsed.publicId()).isEqualTo(generated.publicId());
            assertThat(generated.secretHash()).hasSize(64).isNotEqualTo(parsed.secret());
            assertThat(codec.matches(parsed.secret(), generated.secretHash())).isTrue();
            keys.add(generated.rawKey());
        }
        assertThat(keys).hasSize(100);
    }
    @Test void rejectsMalformedKeysAndWrongSecrets() {
        for (String raw : new String[]{"", "Bearer token", "rp_invalid.secret", " rp_" + "a".repeat(32) + "." + "b".repeat(43)}) {
            assertThat(codec.parse(raw)).isEmpty();
        }
        assertThat(codec.parse(null)).isEmpty();
        var key = codec.generate();
        var other = codec.parse(codec.generate().rawKey()).orElseThrow();
        assertThat(codec.matches(other.secret(), key.secretHash())).isFalse();
        assertThat(codec.matches(other.secret(), "invalid")).isFalse();
    }
    @Test void internalRecordsDoNotPrintSecrets() {
        var key = codec.generate();
        assertThat(key.toString()).doesNotContain(key.rawKey(), key.secretHash());
        var parsed = codec.parse(key.rawKey()).orElseThrow();
        assertThat(parsed.toString()).doesNotContain(parsed.secret());
    }
}
