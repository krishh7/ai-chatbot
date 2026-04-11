package com.coder.ai_chatbot.controller;

import com.coder.ai_chatbot.service.ConversationService;
import com.coder.ai_chatbot.service.ModelService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing AI conversations with memory/history.
 *
 * KEY CONCEPT: Conversation Memory
 *
 * LLMs are STATELESS by default - each API call is independent with no memory
 * of previous interactions. To create a "chatbot" experience where the AI
 * remembers what was said earlier, we must:
 *
 * 1. Store conversation history on our side (server or client)
 * 2. Send the FULL relevant history with each new message
 * 3. Manage token limits (LLMs have context window limits)
 *
 * This controller demonstrates this pattern using:
 * - ConversationService: Stores and manages message history per conversation
 * - conversationId: Unique identifier to track separate conversations
 * - Message objects: Spring AI's representation of chat messages (user/assistant/system)
 *
 * REAL-WORLD APPLICATIONS:
 * - Customer support chatbots that remember user context
 * - Coding assistants that understand your project across messages
 * - Personal assistants that maintain context throughout a session
 *
 * @author HungryCoders
 */
@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    /**
     * Service for resolving the appropriate ChatClient based on provider.
     */
    private final ModelService modelService;

    /**
     * Service that manages conversation history storage and retrieval.
     * Handles token counting and message truncation to fit context windows.
     */
    private final ConversationService conversationService;

    /**
     * Send a message within a conversation context (with memory).
     *
     * ENDPOINT: POST /api/conversation/{conversationId}
     *
     * PATH VARIABLE:
     * - conversationId: Unique identifier for this conversation session.
     *                   Use any string (UUID recommended for production).
     *                   Same ID = same conversation history.
     *
     * REQUEST HEADERS:
     * - AI-Provider (optional): "openai" or "gemini". Defaults to "openai"
     * - AI-Model (optional): Override model (e.g., "gpt-4o", "gemini-1.5-pro")
     *
     * REQUEST BODY: Plain text message from the user
     *
     * RESPONSE (JSON):
     * {
     *     "conversationId": "abc-123",
     *     "response": "AI's response here...",
     *     "messageCount": 5,
     *     "totalTokens": 1250
     * }
     *
     * EXAMPLE CONVERSATION FLOW:
     *
     * Request 1:
     * POST /api/conversation/session-123
     * Body: "My name is Aman"
     * Response: "Nice to meet you, Aman! How can I help you today?"
     *
     * Request 2 (same conversationId):
     * POST /api/conversation/session-123
     * Body: "What is my name?"
     * Response: "Your name is Aman, as you mentioned earlier!"
     *
     * The AI remembers because we send the full history with each request!
     *
     * @param conversationId Unique identifier for this conversation
     * @param provider AI provider to use (header)
     * @param model Optional model override (header)
     * @param message User's message (body)
     * @return Response with AI reply and conversation metadata
     */
    @PostMapping("/{conversationId}")
    public Map<String, Object> chat(
            @PathVariable String conversationId,
            @RequestHeader(value = "AI-Provider", defaultValue = "openai") String provider,
            @RequestHeader(value = "AI-Model", required = false) String model,
            @RequestBody String message
    ) {

        // Validate: For non-OpenAI providers, model is mandatory
        if (!"openai".equalsIgnoreCase(provider) &&
                (model == null || model.isEmpty())) {
            return Map.of(
                    "error", true,
                    "message", "AI-Model header is required when using provider: " + provider
            );
        }

        // ==================== STEP 1: STORE USER MESSAGE ====================
        // Before calling the AI, save the user's message to conversation history.
        // This ensures the message is included in the context for this and future calls.
        conversationService.addUserMessage(conversationId, message);

        // ==================== STEP 2: RETRIEVE CONVERSATION HISTORY ====================
        // KEY CONCEPT: Token Management
        //
        // LLMs have a maximum "context window" (e.g., GPT-4 has 128K tokens).
        // We can't send infinite history - we must truncate older messages.
        //
        // getRecentMessages() returns messages that fit within token limits,
        // typically keeping the most recent messages and dropping older ones.
        // This is a common strategy called "sliding window" or "truncation".
        List<Message> history = conversationService.getRecentMessages(conversationId);

        log.info("=== REQUEST RECEIVED ===");
        log.info("Provider: {}", provider);
        log.info("Model header: {}", model);
        log.info("Message: {}", message);

        // ==================== STEP 3: GET AI CLIENT ====================
        ChatClient chatClient = modelService.getChatClient(provider);
        log.info("ChatClient class: {}", chatClient.getClass().getName());
        log.info("ChatClient: {}", chatClient);

        // ==================== STEP 4: BUILD PROMPT WITH HISTORY ====================
        // KEY CONCEPT: .messages(history)
        //
        // Unlike the simple ChatController that only sends .user(message),
        // here we use .messages(history) to send the ENTIRE conversation.
        //
        // The history List<Message> contains:
        // - UserMessage objects (what the human said)
        // - AssistantMessage objects (what the AI replied)
        // - Optionally SystemMessage (instructions for the AI)
        //
        // Spring AI's ChatClient will format these correctly for the LLM API.
        var promptSpec = chatClient.prompt().messages(history);

        // ==================== STEP 5: ADD MODEL OPTIONS IF SPECIFIED ====================
        // Optional: Override the default model at runtime
        if (model != null && !model.isEmpty()) {
            promptSpec = promptSpec.options(
                    OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(1.0)
                            .build()
            );
        }

        log.info("Using default model from ChatClient bean");
        log.info("=== CALLING PROMPT ===");

        // ==================== STEP 6: EXECUTE AI REQUEST ====================
        // KEY CONCEPT: ChatResponse vs .content()
        //
        // Previously we used .call().content() to get just the text.
        // Here we use .call().chatResponse() to get the FULL response object.
        //
        // ChatResponse contains:
        // - result: The AI's response (ChatGeneration)
        // - metadata: Token usage, model info, finish reason, etc.
        //
        // This is useful when you need more than just the text output.
        ChatResponse response = promptSpec.call().chatResponse();
        String aiResponse = response.getResult().getOutput().getText();

        log.info("aiResponse = "+ aiResponse);

        // ==================== STEP 7: STORE AI RESPONSE ====================
        // Save the assistant's response to history for future context.
        // Next time the user sends a message, this response will be included.
        conversationService.addAssistantMessage(conversationId, aiResponse);

        // ==================== STEP 8: RETURN RESPONSE WITH METADATA ====================
        // Include useful metadata for debugging and monitoring:
        // - messageCount: How many messages in this conversation
        // - totalTokens: Approximate token usage (for cost tracking)
        return Map.of(
                "conversationId", conversationId,
                "response", aiResponse,
                "messageCount", conversationService.getConversationInfo(conversationId).get("messageCount"),
                "totalTokens", conversationService.getConversationInfo(conversationId).get("totalTokens")
        );
    }

    /**
     * Get metadata about a conversation without retrieving full history.
     *
     * ENDPOINT: GET /api/conversation/{conversationId}/info
     *
     * Useful for:
     * - Dashboard displays showing conversation stats
     * - Monitoring token usage before it hits limits
     * - Debugging conversation state
     *
     * @param conversationId The conversation to query
     * @return Metadata about the conversation (messageCount, totalTokens, etc.)
     */
    @GetMapping("/{conversationId}/info")
    public Map<String, Object> getInfo(@PathVariable String conversationId) {
        return conversationService.getConversationInfo(conversationId);
    }

    /**
     * Retrieve the full conversation history.
     *
     * ENDPOINT: GET /api/conversation/{conversationId}/history
     *
     * Returns all messages in the conversation, formatted as:
     * {
     *     "conversationId": "abc-123",
     *     "messages": [
     *         { "role": "user", "content": "Hello!" },
     *         { "role": "assistant", "content": "Hi there!" },
     *         ...
     *     ]
     * }
     *
     * Useful for:
     * - Displaying conversation in a UI
     * - Exporting conversation logs
     * - Debugging message flow
     *
     * @param conversationId The conversation to retrieve
     * @return Full message history with roles
     */
    @GetMapping("/{conversationId}/history")
    public Map<String, Object> getHistory(@PathVariable String conversationId) {
        List<Message> messages = conversationService.getMessages(conversationId);

        // Transform Message objects into a clean JSON-friendly format
        // msg.getMessageType().getValue() returns "user", "assistant", or "system"
        return Map.of(
                "conversationId", conversationId,
                "messages", messages.stream()
                        .map(msg -> Map.of(
                                "role", msg.getMessageType().getValue(),
                                "content", msg.toString()
                        ))
                        .toList()
        );
    }

    /**
     * Clear/delete a conversation's history.
     *
     * ENDPOINT: DELETE /api/conversation/{conversationId}
     *
     * Useful for:
     * - "New conversation" button in UI
     * - Privacy compliance (user requests data deletion)
     * - Freeing memory in long-running applications
     *
     * @param conversationId The conversation to clear
     * @return Confirmation message
     */
    @DeleteMapping("/{conversationId}")
    public Map<String, Object> clearConversation(@PathVariable String conversationId) {
        conversationService.clearConversation(conversationId);
        return Map.of(
                "message", "Conversation cleared",
                "conversationId", conversationId
        );
    }

    /**
     * List all active conversations.
     *
     * ENDPOINT: GET /api/conversation/list
     *
     * Returns a list of all conversation IDs currently stored.
     * Useful for admin dashboards or conversation management UIs.
     *
     * NOTE: In production, you'd likely add:
     * - Pagination
     * - User-based filtering (only show user's own conversations)
     * - Metadata like last activity timestamp
     *
     * @return List of all conversation identifiers
     */
    @GetMapping("/list")
    public Map<String, Object> listConversations() {
        return Map.of(
                "conversations", conversationService.listConversations()
        );
    }
}
