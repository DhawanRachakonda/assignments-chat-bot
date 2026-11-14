package com.alumnx.chatbot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Thin client for the Trainer Central assignments API, with a short in-memory cache. */
@Component
public class EntriesClient {

    record Page(int total, List<Entry> items) {}

    private final RestClient http;
    private final int pageSize;
    private final Duration ttl;
    private volatile List<Entry> cache = List.of();
    private volatile long cachedAt = 0;

    public EntriesClient(AppProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.entriesApi().baseUrl()).build();
        this.pageSize = props.entriesApi().pageSize();
        this.ttl = Duration.ofSeconds(props.entriesApi().cacheTtlSeconds());
    }

    /** All entries, newest first, paging through the API until exhausted. */
    public synchronized List<Entry> all() {
        if (System.currentTimeMillis() - cachedAt < ttl.toMillis() && !cache.isEmpty()) {
            return cache;
        }
        List<Entry> out = new ArrayList<>();
        int skip = 0;
        while (true) {
            final int s = skip;
            Page page = http.get()
                    .uri(u -> u.path("/api/entries").queryParam("limit", pageSize).queryParam("skip", s).build())
                    .retrieve().body(Page.class);
            if (page == null || page.items() == null || page.items().isEmpty()) break;
            out.addAll(page.items());
            skip += page.items().size();
            if (skip >= page.total()) break;
        }
        cache = List.copyOf(out);
        cachedAt = System.currentTimeMillis();
        return cache;
    }

    /** The API's own aggregate endpoints, passed through untouched. */
    public Object stats() {
        return http.get().uri("/api/stats").retrieve().body(Map.class);
    }

    public Object learners() {
        return http.get().uri("/api/learners").retrieve().body(Map.class);
    }
}
