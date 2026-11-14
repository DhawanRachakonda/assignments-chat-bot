package com.alumnx.chatbot;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record ChatRequest(String message, String conversationId) {}

    private final ChatClient chat;

    public ChatController(ChatClient chat) {
        this.chat = chat;
    }

    /** Streams the answer token-by-token as Server-Sent Events. */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest req) {
        String cid = req.conversationId() == null || req.conversationId().isBlank() ? "default" : req.conversationId();
        return chat.prompt()
                .user(req.message())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .stream()
                .content()
                .map(token -> ServerSentEvent.<String>builder().event("token").data(token).build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder().event("done").data("[DONE]").build()))
                .onErrorResume(ex -> Flux.just(ServerSentEvent.<String>builder().event("error")
                        .data("The assistant is unavailable: " + ex.getMessage()).build()));
    }
}
