package com.alumnx.chatbot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(EntriesApi entriesApi, String corsAllowedOrigins) {
    public record EntriesApi(String baseUrl, int pageSize, int cacheTtlSeconds) {}
}
