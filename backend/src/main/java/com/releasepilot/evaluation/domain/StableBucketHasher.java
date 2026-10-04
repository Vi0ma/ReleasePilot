package com.releasepilot.evaluation.domain;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class StableBucketHasher {
    public int bucket(String userKey, String flagKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userKey + ":" + flagKey).getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, digest).mod(BigInteger.valueOf(100)).intValueExact();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
