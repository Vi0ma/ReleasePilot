package com.releasepilot.apikey.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyCodec {
    private static final Pattern FORMAT = Pattern.compile("^rp_([a-f0-9]{32})\\.([A-Za-z0-9_-]{43})$");
    private final SecureRandom random = new SecureRandom();

    public Generated generate() {
        byte[] identifier = new byte[16];
        byte[] secretBytes = new byte[32];
        random.nextBytes(identifier);
        random.nextBytes(secretBytes);
        String publicId = HexFormat.of().formatHex(identifier);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        return new Generated(publicId, HexFormat.of().formatHex(digest(secret)), "rp_" + publicId + "." + secret);
    }
    public Optional<Parsed> parse(String raw) {
        if (raw == null) { return Optional.empty(); }
        var matcher = FORMAT.matcher(raw);
        return matcher.matches() ? Optional.of(new Parsed(matcher.group(1), matcher.group(2))) : Optional.empty();
    }
    public boolean matches(String secret, String expectedHash) {
        try { return MessageDigest.isEqual(digest(secret), HexFormat.of().parseHex(expectedHash)); }
        catch (IllegalArgumentException ex) { return false; }
    }
    private byte[] digest(String secret) {
        try { return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    public record Generated(String publicId, String secretHash, String rawKey) {
        @Override public String toString() { return "GeneratedApiKey[redacted]"; }
    }
    public record Parsed(String publicId, String secret) {
        @Override public String toString() { return "ParsedApiKey[redacted]"; }
    }
}
