package com.voice.agent.auth;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);

    private final byte[] secret;
    private final long expireSeconds;

    public AuthTokenService(
            @Value("${voicecal.auth.secret}") String secret,
            @Value("${voicecal.auth.expire-hours:24}") long expireHours
    ) {
        this.secret = resolveSecret(secret);
        this.expireSeconds = expireHours * 60 * 60;
    }

    private byte[] resolveSecret(String configuredSecret) {
        if (StringUtils.hasText(configuredSecret)) {
            return configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        byte[] generatedSecret = new byte[32];
        new SecureRandom().nextBytes(generatedSecret);
        log.warn("VOICECAL_AUTH_SECRET is not configured; using a temporary login secret for this process");
        return generatedSecret;
    }

    public String createToken(Long userId) {
        long expiresAt = Instant.now().getEpochSecond() + expireSeconds;
        String payload = encode(userId + ":" + expiresAt);
        return payload + "." + sign(payload);
    }

    public Long parseUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !MessageDigest.isEqual(
                sign(parts[0]).getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8)
        )) {
            return null;
        }
        try {
            String[] payload = decode(parts[0]).split(":", -1);
            if (payload.length != 2 || Long.parseLong(payload[1]) <= Instant.now().getEpochSecond()) {
                return null;
            }
            return Long.parseLong(payload[0]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成登录令牌", e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
