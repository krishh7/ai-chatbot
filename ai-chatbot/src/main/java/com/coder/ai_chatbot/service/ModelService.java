package com.coder.ai_chatbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ModelService {

    @Autowired
    @Qualifier("openAIChatClient")
    private ChatClient openAIChatClient;

    @Autowired
    @Qualifier("geminiChatClient")
    private ChatClient geminiChatClient;

    public ChatClient getChatClient(String provider) {
        if (provider == null || provider.isEmpty()) {
            return openAIChatClient;
        }
        return switch (provider.toLowerCase()) {
            case "open-ai" -> openAIChatClient;
            case "gemini" -> geminiChatClient;
            default -> throw new IllegalStateException("Unknown provider: " + provider.toLowerCase());
        };
    }
}
