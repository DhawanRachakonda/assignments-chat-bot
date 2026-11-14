package com.alumnx.chatbot;

/** One record from the assignments API. Unknown JSON fields are ignored by default in Boot 4 (Jackson 3). */
public record Entry(String id, String text, String sender, String created_at) {}
