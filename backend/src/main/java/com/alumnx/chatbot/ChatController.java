package com.alumnx.chatbot;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record ChatRequest(String message, String conversationId) {}

    private static final int MAX_CONVERSATION_ID_LENGTH = 64;

    private final ChatClient chat;
    private final int maxMessageLength;

    public ChatController(ChatClient chat, AppProperties props) {
        this.chat = chat;
        this.maxMessageLength = props.limits().messageMaxLength();
    }

    /** Streams the answer token-by-token as Server-Sent Events. */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest req) {
        String message = req.message() == null ? "" : req.message().trim();
        if (message.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a question.");
        }
        if (message.length() > maxMessageLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Question is too long (max " + maxMessageLength + " characters).");
        }
        String cid = req.conversationId() == null || req.conversationId().isBlank()
                ? "default"
                : req.conversationId().substring(0, Math.min(req.conversationId().length(), MAX_CONVERSATION_ID_LENGTH));
        return chat.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .stream()
                .content()
                .map(token -> ServerSentEvent.<String>builder().event("token").data(token).build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder().event("done").data("[DONE]").build()))
                .onErrorResume(ex -> Flux.just(ServerSentEvent.<String>builder().event("error")
                        .data("The assistant is unavailable: " + ex.getMessage()).build()));
    }
}
