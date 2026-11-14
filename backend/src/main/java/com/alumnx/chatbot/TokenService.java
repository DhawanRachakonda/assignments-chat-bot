package com.alumnx.chatbot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Stateless signed session tokens: "expiryEpochSeconds:nonce.signature" (HMAC-SHA256). The signing key is
 * derived from the access code, so changing APP_ACCESS_CODE invalidates every existing session, and a
 * restart does not log anyone out.
 */
@Component
public class TokenService {

    private final byte[] accessCodeDigest;
    private final byte[] key;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public TokenService(AppProperties props) {
        String code = props.auth().accessCode() == null ? "" : props.auth().accessCode();
        this.accessCodeDigest = sha256(code);
        this.key = sha256("alumnx-chatbot-session:" + code);
        this.ttl = Duration.ofHours(props.auth().tokenTtlHours());
    }

    /** Whether an access code has been configured at all. */
    public boolean configured(AppProperties props) {
        return props.auth().accessCode() != null && !props.auth().accessCode().isBlank();
    }

    /** Constant-time comparison of the submitted code with the configured one. */
    public boolean codeMatches(String submitted) {
        return MessageDigest.isEqual(sha256(submitted == null ? "" : submitted), accessCodeDigest);
    }

    public String issue() {
        byte[] nonce = new byte[8];
        random.nextBytes(nonce);
        long expiry = System.currentTimeMillis() / 1000 + ttl.toSeconds();
        String payload = expiry + ":" + HexFormat.of().formatHex(nonce);
        return payload + "." + sign(payload);
    }

    public boolean valid(String token) {
        if (token == null) return false;
        int dot = token.lastIndexOf('.');
        if (dot <= 0) return false;
        String payload = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        int colon = payload.indexOf(':');
        if (colon <= 0) return false;
        try {
            return Long.parseLong(payload.substring(0, colon)) > System.currentTimeMillis() / 1000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
