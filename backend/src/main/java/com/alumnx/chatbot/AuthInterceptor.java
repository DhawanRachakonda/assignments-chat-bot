package com.alumnx.chatbot;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Requires "Authorization: Bearer <token>" on protected endpoints; answers 401 otherwise. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokens;

    public AuthInterceptor(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true; // CORS preflight
        if (request.getDispatcherType() != DispatcherType.REQUEST) return true; // async re-dispatch of a streamed reply

        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        if (tokens.valid(token)) return true;

        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"Please sign in.\"}");
        return false;
    }
}
