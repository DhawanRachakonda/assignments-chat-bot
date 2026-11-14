package com.alumnx.chatbot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window limiter: per client IP (per minute and per day) plus a global per-minute cap.
 * The global cap bounds total spend even if a client spoofs its IP header. State is per instance and is
 * lost on restart, which is fine for a single small service.
 */
@Component
public class RateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds, String message) {
        static Decision ok() {
            return new Decision(true, 0, null);
        }

        static Decision deny(long retryAfterSeconds, String message) {
            return new Decision(false, Math.max(1, retryAfterSeconds), message);
        }
    }

    private static final long MINUTE_MS = 60_000L;
    private static final long DAY_MS = 24 * 60 * MINUTE_MS;
    private static final int MAX_TRACKED_IPS = 10_000;

    private final AppProperties.Limits limits;
    private final Map<String, Deque<Long>> perIp = new HashMap<>();
    private final Deque<Long> global = new ArrayDeque<>();

    public RateLimiter(AppProperties props) {
        this.limits = props.limits();
    }

    public synchronized Decision tryAcquire(String ip, long now) {
        prune(global, now - MINUTE_MS);
        if (global.size() >= limits.globalPerMinute()) {
            return Decision.deny(secondsUntil(global.peekFirst() + MINUTE_MS, now),
                    "The assistant is busy right now. Please try again in a minute.");
        }

        if (perIp.size() > MAX_TRACKED_IPS) {
            perIp.values().removeIf(d -> d.isEmpty() || d.peekLast() < now - DAY_MS);
        }
        Deque<Long> hits = perIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        prune(hits, now - DAY_MS);

        if (hits.size() >= limits.perIpPerDay()) {
            return Decision.deny(secondsUntil(hits.peekFirst() + DAY_MS, now),
                    "Daily question limit reached (" + limits.perIpPerDay() + "). Please come back later.");
        }
        long inLastMinute = hits.stream().filter(t -> t > now - MINUTE_MS).count();
        if (inLastMinute >= limits.perIpPerMinute()) {
            long oldestInWindow = hits.stream().filter(t -> t > now - MINUTE_MS).findFirst().orElse(now);
            return Decision.deny(secondsUntil(oldestInWindow + MINUTE_MS, now),
                    "Too many questions. Please wait a moment and try again.");
        }

        hits.addLast(now);
        global.addLast(now);
        return Decision.ok();
    }

    private static void prune(Deque<Long> d, long cutoff) {
        while (!d.isEmpty() && d.peekFirst() <= cutoff) d.pollFirst();
    }

    private static long secondsUntil(long time, long now) {
        return (time - now + 999) / 1000;
    }
}
