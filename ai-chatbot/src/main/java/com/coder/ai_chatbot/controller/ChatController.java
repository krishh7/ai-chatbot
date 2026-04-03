package com.coder.ai_chatbot.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient chatClient;

    @PostMapping("/chat")
    public String chat(@RequestBody String messageInput) {
        return chatClient.prompt() //starts building prompt
                .user(messageInput) //add a user message
                .call() //execute the request
                .content(); //extract the text from response
    }
}
