package com.example.httpreading.service;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ModelClient modelClient;

    public ChatService(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public String answer(String question) {
        String content = modelClient.chat(question);
        if (content == null || content.isBlank()) {
            return "";
        }
        return content;
    }
}
