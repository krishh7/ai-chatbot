package com.coder.ai_chatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MultiModelConfig {

    @Value("${gemini.api.key}")
    private String geminiKey;

    @Value("${gemini.api.url}")
    private String geminiUrl;

    @Value("${gemini.api.completions.path}")
    private String geminiCompletionsPath;

    @Value("${gemini.api.model.name}")
    private String geminiModel;

    @Bean("openAIChatClient")
    @Primary
    public ChatClient openAIChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }

    @Bean("geminiChatClient")
    public ChatClient geminiChatClient(OpenAiChatModel openAiChatModel) {
        OpenAiApi geminiApi = OpenAiApi.builder()
                .baseUrl(geminiUrl)
                .completionsPath(geminiCompletionsPath)
                .apiKey(geminiKey)
                .build();

        OpenAiChatModel geminiChatModel = OpenAiChatModel.builder()
                .openAiApi(geminiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(geminiModel)
                        .temperature(1.0).build())
                .build();
        return ChatClient.builder(geminiChatModel).build();
    }
}
