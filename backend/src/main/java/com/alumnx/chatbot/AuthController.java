package com.alumnx.chatbot;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String code) {}

    public record LoginResponse(String token) {}

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_TRACKED_IPS = 10_000;

    private final AppProperties props;
    private final TokenService tokens;
    private final Map<String, Deque<Long>> failures = new HashMap<>();

    public AuthController(AppProperties props, TokenService tokens) {
        this.props = props;
        this.tokens = tokens;
        if (!tokens.configured(props)) {
            log.warn("APP_ACCESS_CODE is not set: all logins will be refused until it is configured.");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        if (!tokens.configured(props)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sign-in is not configured on the server.");
        }
        String ip = RateLimitInterceptor.clientIp(request);
        long now = System.currentTimeMillis();

        long retryAfter = blockedFor(ip, now);
        if (retryAfter > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Try again in " + retryAfter + " seconds.");
        }
        if (!tokens.codeMatches(req.code())) {
            recordFailure(ip, now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect access code.");
        }
        return ResponseEntity.ok(new LoginResponse(tokens.issue()));
    }

    /** Lets the UI check a stored token on page load. The AuthInterceptor rejects invalid tokens with 401. */
    @GetMapping("/session")
    public ResponseEntity<Void> session() {
        return ResponseEntity.noContent().build();
    }

    private synchronized long blockedFor(String ip, long now) {
        Deque<Long> d = failures.get(ip);
        if (d == null) return 0;
        while (!d.isEmpty() && d.peekFirst() <= now - WINDOW_MS) d.pollFirst();
        if (d.size() < props.auth().maxFailedLoginsPerMinute()) return 0;
        return Math.max(1, (d.peekFirst() + WINDOW_MS - now + 999) / 1000);
    }

    private synchronized void recordFailure(String ip, long now) {
        if (failures.size() > MAX_TRACKED_IPS) {
            failures.values().removeIf(d -> d.isEmpty() || d.peekLast() < now - WINDOW_MS);
        }
        failures.computeIfAbsent(ip, k -> new ArrayDeque<>()).addLast(now);
    }
}
