package com.releasepilot.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ISSUER = "https://releasepilot.local";
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final long expirationSeconds;

    public JwtService(@Value("${releasepilot.jwt.secret}") String secret,
                      @Value("${releasepilot.jwt.expiration-seconds}") long expirationSeconds) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(secret); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("JWT_SECRET must be Base64", ex); }
        if (bytes.length < 32) { throw new IllegalStateException("JWT_SECRET must contain at least 32 decoded bytes"); }
        if (expirationSeconds < 60 || expirationSeconds > 86400) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be between 60 and 86400");
        }
        this.expirationSeconds = expirationSeconds;
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(ISSUER), token -> {
                    boolean valid = token.getExpiresAt() != null && token.getIssuedAt() != null
                            && token.getSubject() != null && token.getExpiresAt().isAfter(token.getIssuedAt())
                            && !token.getIssuedAt().isAfter(Instant.now());
                    return valid ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
                }));
    }

    public String generate(Long userId) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(userId.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(expirationSeconds)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public Jwt validate(String token) { return decoder.decode(token); }
    public long expirationSeconds() { return expirationSeconds; }
}
