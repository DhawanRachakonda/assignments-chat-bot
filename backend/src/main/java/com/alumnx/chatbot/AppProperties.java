package com.alumnx.chatbot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(EntriesApi entriesApi, String corsAllowedOrigins, Limits limits, Auth auth) {

    public record EntriesApi(String baseUrl, int pageSize, int cacheTtlSeconds) {}

    /** Abuse protection for the public chat endpoint (each question costs several model calls). */
    public record Limits(int messageMaxLength, int perIpPerMinute, int perIpPerDay, int globalPerMinute) {}

    /** Access-code login. If accessCode is blank, every login is refused (fail closed). */
    public record Auth(String accessCode, int tokenTtlHours, int maxFailedLoginsPerMinute) {}
}
