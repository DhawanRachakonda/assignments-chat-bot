package com.alumnx.chatbot;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Turns validation errors into {"message": "..."} JSON the UI can show (the chat endpoint itself produces SSE). */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<String> handle(ResponseStatusException ex) {
        String msg = ex.getReason() == null ? "Request failed." : ex.getReason();
        String json = "{\"message\":\"" + msg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(json);
    }
}
