package com.coder.ai_chatbot.model;

import org.springframework.ai.chat.messages.Message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a conversation's history with an AI.
 *
 * KEY CONCEPT: Conversation State Management
 *
 * Since LLMs are stateless (each API call is independent), we need to:
 * 1. Store all messages exchanged in a conversation
 * 2. Replay relevant history with each new request
 * 3. Manage token limits to avoid exceeding context windows
 *
 * This class encapsulates:
 * - Message storage (user and assistant messages)
 * - Token counting (approximate estimation)
 * - Sliding window retrieval (get recent messages within token budget)
 * - Metadata tracking (timestamps, message counts)
 *
 * CONTEXT WINDOW LIMITS (as of 2024):
 * - GPT-3.5-turbo: 16K tokens
 * - GPT-4: 8K-128K tokens (depending on variant)
 * - GPT-4o: 128K tokens
 * - Gemini 1.5 Pro: 1M+ tokens
 *
 * Even with large context windows, managing history is important for:
 * - Cost optimization (more tokens = higher cost)
 * - Response quality (too much irrelevant context can confuse the model)
 * - Latency (more tokens = slower processing)
 *
 * @author HungryCoders
 */
public class ConversationHistory {

    /**
     * Unique identifier for this conversation.
     * Used to retrieve and manage specific conversation sessions.
     */
    private String conversationId;

    /**
     * List of all messages in this conversation.
     * Contains both UserMessage and AssistantMessage objects from Spring AI.
     * Messages are stored in chronological order (oldest first).
     */
    private List<Message> messages;

    /**
     * Timestamp when this conversation was created.
     * Useful for analytics, cleanup jobs, and debugging.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last message added.
     * Updated every time a new message is added.
     * Useful for identifying stale/inactive conversations.
     */
    private LocalDateTime updatedAt;

    /**
     * Running total of estimated tokens in this conversation.
     * Used for quick token budget checks without recalculating.
     *
     * NOTE: This is an APPROXIMATION. Actual token counts depend on
     * the specific tokenizer used by the model (GPT uses tiktoken,
     * other models have different tokenizers).
     */
    private int totalTokens;

    /**
     * Creates a new conversation history with the given ID.
     *
     * @param conversationId Unique identifier for this conversation
     */
    public ConversationHistory(String conversationId) {
        this.conversationId = conversationId;
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.totalTokens = 0;
    }

    /**
     * Adds a message to the conversation history.
     *
     * This method:
     * 1. Appends the message to the history list
     * 2. Updates the "last updated" timestamp
     * 3. Estimates and adds the token count
     *
     * TOKEN ESTIMATION:
     * We use a simple heuristic: ~4 characters per token.
     *
     * This is a rough approximation based on English text:
     * - "Hello" (5 chars) ≈ 1-2 tokens
     * - "authentication" (14 chars) ≈ 3-4 tokens
     * - Code and special characters may tokenize differently
     *
     * For PRODUCTION applications, consider using:
     * - tiktoken library (OpenAI's actual tokenizer)
     * - Model-specific tokenizer APIs
     * - Spring AI's built-in token counting (if available)
     *
     * @param message The Message (UserMessage or AssistantMessage) to add
     */
    public void addMessage(Message message) {
        this.messages.add(message);
        this.updatedAt = LocalDateTime.now();
        // Simple token estimation: ~4 characters per token
        // This is a common heuristic for English text
        this.totalTokens += message.getText().length() / 4;
    }

    /**
     * Returns a COPY of all messages in this conversation.
     *
     * WHY RETURN A COPY?
     * - Encapsulation: Prevents external code from modifying internal state
     * - Thread safety: Callers can iterate without ConcurrentModificationException
     * - Predictability: Changes to returned list don't affect conversation
     *
     * This is a defensive programming best practice.
     *
     * @return A new ArrayList containing all messages (safe to modify)
     */
    public List<Message> getMessages() {
        return new ArrayList<>(messages); // Return copy
    }

    /**
     * Retrieves recent messages that fit within a token budget.
     *
     * KEY CONCEPT: Sliding Window / Truncation Strategy
     *
     * When conversation history exceeds the model's context window,
     * we must decide which messages to keep. Common strategies:
     *
     * 1. SLIDING WINDOW (used here):
     *    Keep the most recent N messages/tokens
     *    Pros: Recent context is most relevant
     *    Cons: Loses early conversation setup
     *
     * 2. SUMMARIZATION:
     *    Use AI to summarize old messages, keep summary + recent
     *    Pros: Retains key information
     *    Cons: Adds latency and cost
     *
     * 3. SELECTIVE RETENTION:
     *    Keep system prompt + first message + recent messages
     *    Pros: Preserves context setup
     *    Cons: More complex logic
     *
     * ALGORITHM:
     * 1. Start from the NEWEST message
     * 2. Add messages while within token budget
     * 3. Stop when adding next message would exceed limit
     * 4. Return messages in chronological order
     *
     * EXAMPLE:
     * Messages: [M1, M2, M3, M4, M5] (M5 is newest)
     * Token budget: 100
     * If M5=30, M4=40, M3=50 tokens:
     * - Add M5 (total: 30) ✓
     * - Add M4 (total: 70) ✓
     * - Add M3 (total: 120) ✗ exceeds budget
     * Result: [M4, M5]
     *
     * @param maxTokens Maximum token budget for returned messages
     * @return List of recent messages fitting within the token limit
     */
    public List<Message> getRecentMessages(int maxTokens) {
        // Sliding window: keep recent messages within token limit
        List<Message> recentMessages = new ArrayList<>();
        int currentTokens = 0;

        // Iterate from newest to oldest (reverse order)
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            int msgTokens = msg.getText().length() / 4;  // Same estimation heuristic

            // Check if adding this message would exceed the budget
            if (currentTokens + msgTokens > maxTokens) {
                break; // Would exceed limit, stop here
            }

            // Add at index 0 to maintain chronological order
            // (since we're iterating backwards)
            recentMessages.add(0, msg);
            currentTokens += msgTokens;
        }

        return recentMessages;
    }

    // ==================== GETTERS ====================
    // Standard getters for accessing conversation metadata

    /**
     * @return The unique identifier for this conversation
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * @return Total number of messages in the conversation
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * @return Estimated total tokens across all messages
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    /**
     * @return Timestamp when conversation was created
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * @return Timestamp of the most recent message
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
