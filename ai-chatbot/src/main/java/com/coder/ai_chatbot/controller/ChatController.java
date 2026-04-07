package com.coder.ai_chatbot.controller;

import com.coder.ai_chatbot.service.ModelService;
import com.coder.ai_chatbot.service.OpenAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("ChatBot/api")
@RequiredArgsConstructor
@Log4j2
public class ChatController {
    private final OpenAIService openAIService;
    private final ModelService modelService;
    public static final String AI_MODEL = "AI-Model";
    public static final String AI_PROVIDER = "AI-Provider";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_PROVIDER = "open-ai";

    //Flux is a stream f zero or more items
    //stream of text chunks, it is a prt of project react
    @GetMapping("/stream") //stream?provider=openai&
    public Flux<String> prompt(@RequestParam(required = false) String model,
                               @RequestParam(required = false) String provider,
                               @RequestParam("message") String inputMessage) {

        log.info("=== REQUEST RECEIVED ===");
        log.info("Provider: {}", provider);
        log.info("Model: {}", model);
        log.info("Message: {}", inputMessage);
        String selectedModel = (model != null && !model.isEmpty()) ? model : DEFAULT_MODEL;
//        String selectedProvider = (provider != null && !provider.isBlank()) ? provider : DEFAULT_PROVIDER;
        ChatClient chatClient = modelService.getChatClient(provider);
        log.info("ChatClient class: {}", chatClient.getClass().getName());
        log.info("ChatClient: {}", chatClient);

        if (model != null && !model.isEmpty()) {
            return chatClient.prompt() //starts building prompt
                    .user(inputMessage) //add a user message
                    .options(OpenAiChatOptions.builder().model(selectedModel).maxCompletionTokens(500).build()) //Adds models
                    .stream()
                    .content(); //extract the text from response
        }

        return chatClient.prompt() //starts building prompt
                .user(inputMessage) //add a user message
                .stream()
                .content(); //extract the text from response
    }

    @GetMapping("/models")
    public String getModels() {
        return openAIService.getModels();
    }
}
