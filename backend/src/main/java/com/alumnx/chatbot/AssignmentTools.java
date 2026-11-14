package com.alumnx.chatbot;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Tools the model can call to read live assignment data. */
@Component
public class AssignmentTools {

    private final EntriesClient client;

    public AssignmentTools(EntriesClient client) {
        this.client = client;
    }

    @Tool(description = """
            Returns EVERY raw entry (id, sender, created_at, text) from the assignments API, newest first, \
            plus the total count. Use this for counting, ranking, de-duplicating, or any question that needs \
            the full data set. Entry text is free-form and may be a TrainerCentral e-mail, a short note, \
            or a test message.""")
    public Map<String, Object> getAllEntries() {
        List<Entry> all = client.all();
        return Map.of("total", all.size(), "entries", all);
    }

    @Tool(description = "Case-insensitive search over entry text and sender name. Use for questions about a "
            + "specific learner, assignment topic, course, e-mail address or message id.")
    public List<Entry> searchEntries(@ToolParam(description = "Text to look for") String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return client.all().stream()
                .filter(e -> (e.text() != null && e.text().toLowerCase(Locale.ROOT).contains(q))
                        || (e.sender() != null && e.sender().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
    }

    public record Submission(
            @ToolParam(description = "Canonical learner name, aliases already merged") String learner,
            @ToolParam(description = "Assignment topic, or 'Unknown' if the entry does not say") String assignment) {}

    @Tool(description = """
            Exact counter. After you have read the entries, de-duplicated them (one item per distinct submission) \
            and excluded test/junk entries, pass that list here. Returns the exact number of distinct submissions, \
            number of distinct learners, and a per-learner ranking. ALWAYS use this tool's numbers for any \
            count, total or 'who submitted the most' answer instead of counting yourself.""")
    public Map<String, Object> tallySubmissions(
            @ToolParam(description = "One item per distinct submission, tests excluded") List<Submission> submissions) {
        Map<String, java.util.Set<String>> byLearner = new java.util.LinkedHashMap<>();
        for (Submission s : submissions) {
            byLearner.computeIfAbsent(s.learner().trim(), k -> new java.util.LinkedHashSet<>())
                    .add(s.assignment() == null ? "Unknown" : s.assignment().trim());
        }
        // A truncated topic ("FDE Case") is the same assignment as the full title that starts with it.
        byLearner.replaceAll((learner, topics) -> {
            java.util.Set<String> kept = new java.util.LinkedHashSet<>(topics);
            for (String a : topics) {
                for (String b : topics) {
                    if (!a.equals(b) && !a.equalsIgnoreCase("Unknown")
                            && b.toLowerCase(Locale.ROOT).startsWith(a.toLowerCase(Locale.ROOT))) {
                        kept.remove(a);
                    }
                }
            }
            return kept;
        });
        List<Map<String, Object>> ranking = byLearner.entrySet().stream()
                .map(e -> Map.<String, Object>of("learner", e.getKey(), "submissions", e.getValue().size(),
                        "assignments", e.getValue()))
                .sorted((a, b) -> (int) b.get("submissions") - (int) a.get("submissions"))
                .toList();
        int total = ranking.stream().mapToInt(r -> (int) r.get("submissions")).sum();
        int max = ranking.isEmpty() ? 0 : (int) ranking.get(0).get("submissions");
        List<Object> top = ranking.stream().filter(r -> (int) r.get("submissions") == max)
                .map(r -> r.get("learner")).toList();
        return Map.of("distinctSubmissions", total, "distinctLearners", byLearner.size(),
                "maxSubmissions", max, "topLearners", top, "ranking", ranking);
    }

    @Tool(description = "The API's pre-computed stats: total entries, top senders by raw entry count, entries per day. "
            + "Counts are RAW (not de-duplicated).")
    public Object getStats() {
        return client.stats();
    }

    @Tool(description = "The API's per-learner list: learner (sender) name, raw submission count, last submission time. "
            + "Counts are RAW (not de-duplicated, aliases not merged).")
    public Object getLearners() {
        return client.learners();
    }
}
