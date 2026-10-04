package com.releasepilot.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final JwtService service = new JwtService(SECRET, 900);

    @Test
    void issuedTokenHasIdentityIssuerAndConfiguredExpiration() {
        var jwt = service.validate(service.generate(42L));
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://releasepilot.local");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
    }

    @Test
    void expiredWrongIssuerMissingExpirationAndWrongSignatureAreRejected() {
        var now = Instant.now();
        assertThatThrownBy(() -> service.validate(token("https://releasepilot.local", now.minusSeconds(120), now.minusSeconds(60))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.validate(token("https://other.local", now.minusSeconds(1), now.plusSeconds(60))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.validate(token("https://releasepilot.local", now.minusSeconds(1), null)))
                .isInstanceOf(JwtException.class);
        String valid = service.generate(42L);
        var differentKey = new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 900);
        assertThatThrownBy(() -> differentKey.validate(valid)).isInstanceOf(JwtException.class);
    }

    @Test
    void invalidKeyAndExpirationFailAtStartup() {
        assertThatThrownBy(() -> new JwtService("invalid-base64!", 900)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("YQ==", 900)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(SECRET, 0)).isInstanceOf(IllegalStateException.class);
    }

    private String token(String issuer, Instant issuedAt, Instant expiresAt) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256")));
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("42").issuedAt(issuedAt);
        if (expiresAt != null) { claims.expiresAt(expiresAt); }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
