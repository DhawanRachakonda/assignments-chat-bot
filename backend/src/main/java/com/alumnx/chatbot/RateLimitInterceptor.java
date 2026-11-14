package com.alumnx.chatbot;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects over-limit chat requests with HTTP 429. It is an interceptor (not a servlet filter) so Spring's
 * CORS headers are already on the response and the browser can show the message instead of a CORS error.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;

    public RateLimitInterceptor(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!HttpMethod.POST.matches(request.getMethod())) return true;
        // Streaming responses re-enter the chain on the async dispatch; count each question once.
        if (request.getDispatcherType() != DispatcherType.REQUEST) return true;

        RateLimiter.Decision d = limiter.tryAcquire(clientIp(request), System.currentTimeMillis());
        if (d.allowed()) return true;

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String msg = d.message().replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"message\":\"" + msg + "\"}");
        return false;
    }

    /** Render puts the caller in X-Forwarded-For. A spoofed header only defeats the per-IP cap, not the global one. */
    static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
