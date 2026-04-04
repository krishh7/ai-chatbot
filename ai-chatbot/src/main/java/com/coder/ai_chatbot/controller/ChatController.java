package com.coder.ai_chatbot.controller;

import com.coder.ai_chatbot.service.ModelService;
import com.coder.ai_chatbot.service.OpenAIService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("ChatBot/api")
@RequiredArgsConstructor
public class ChatController {
    private final OpenAIService openAIService;
    private final ModelService modelService;
    public static final String AI_MODEL = "AI-Model";
    public static final String AI_PROVIDER = "AI-Provider";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_PROVIDER = "open-ai";

    @PostMapping("/prompt")
    public String prompt(@RequestHeader(value = AI_MODEL, required = false) String model,
                         @RequestHeader(value = AI_PROVIDER, required = false) String provider,
                         @RequestBody String inputMessage) {
        String selectedModel = (model != null && !model.isEmpty()) ? model : DEFAULT_MODEL;
//        String selectedProvider = (provider != null && !provider.isBlank()) ? provider : DEFAULT_PROVIDER;
        ChatClient chatClient = modelService.getChatClient(provider);

        if (model != null && !model.isEmpty()) {
            return chatClient.prompt() //starts building prompt
                    .user(inputMessage) //add a user message
                    .options(OpenAiChatOptions.builder().model(selectedModel).maxCompletionTokens(500).build()) //Adds models
                    .call() //execute the request
                    .content(); //extract the text from response
        }

        return chatClient.prompt() //starts building prompt
                .user(inputMessage) //add a user message
                .call() //execute the request
                .content(); //extract the text from response
    }

    @GetMapping("/models")
    public String getModels() {
        return openAIService.getModels();
    }
}
