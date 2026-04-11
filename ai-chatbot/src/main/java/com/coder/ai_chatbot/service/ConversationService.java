package com.coder.ai_chatbot.service;

import com.coder.ai_chatbot.model.ConversationHistory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing conversation history and state.
 *
 * KEY CONCEPT: Conversation Memory Management
 *
 * LLMs are STATELESS - they don't remember previous messages in a conversation.
 * This service acts as the "memory" layer, storing conversation history so we
 * can replay it with each new AI request.
 *
 * RESPONSIBILITIES:
 * 1. Store conversations (in-memory, keyed by conversationId)
 * 2. Add user and assistant messages to conversations
 * 3. Retrieve messages (all or within token limits)
 * 4. Manage conversation lifecycle (create, clear, list)
 *
 * STORAGE STRATEGY: In-Memory (ConcurrentHashMap)
 *
 * PROS:
 * - Fast access (O(1) lookup)
 * - Simple implementation
 * - Good for development/prototyping
 *
 * CONS:
 * - Data lost on server restart
 * - Doesn't scale horizontally (each server has its own memory)
 * - Memory grows with active conversations
 *
 * PRODUCTION ALTERNATIVES:
 * - Redis: Fast, supports TTL, horizontally scalable
 * - PostgreSQL/MongoDB: Persistent, queryable
 * - Spring AI's built-in ChatMemory implementations
 *
 * @author HungryCoders
 */
@Service
public class ConversationService {

    /**
     * In-memory storage for all active conversations.
     *
     * KEY: conversationId (String) - unique identifier for each conversation
     * VALUE: ConversationHistory - contains all messages and metadata
     *
     * WHY ConcurrentHashMap?
     * - Thread-safe: Multiple HTTP requests can access simultaneously
     * - Lock-free reads: High performance under concurrent access
     * - Segment-level locking: Writes don't block the entire map
     *
     * IMPORTANT: In a multi-server deployment, this won't work!
     * User A might hit Server 1, User A's next request might hit Server 2,
     * and Server 2 won't have the conversation history.
     * Solution: Use Redis or a database for shared state.
     */
    private final Map<String, ConversationHistory> conversations = new ConcurrentHashMap<>();

    /**
     * Default token limit for sliding window retrieval.
     *
     * WHY 4000 TOKENS?
     * - Leaves room for the AI's response (~4K tokens)
     * - Safe for most models (GPT-3.5 has 16K, GPT-4o has 128K)
     * - Balances context retention vs. cost/latency
     *
     * TUNING CONSIDERATIONS:
     * - Lower (2000): Faster, cheaper, less context
     * - Higher (8000): More context, but watch for model limits
     * - Model-specific: Adjust based on which model is being used
     */
    private static final int DEFAULT_TOKEN_LIMIT = 4000;

    /**
     * Retrieves an existing conversation or creates a new one.
     *
     * KEY CONCEPT: computeIfAbsent Pattern
     *
     * This is a powerful ConcurrentHashMap method that atomically:
     * 1. Checks if key exists
     * 2. If not, creates new value using the lambda
     * 3. Returns the existing or newly created value
     *
     * All in one atomic operation - no race conditions!
     *
     * WITHOUT computeIfAbsent (race condition possible):
     * ```java
     * if (!conversations.containsKey(id)) {           // Thread A checks
     *     conversations.put(id, new History(id));     // Thread B also creates!
     * }                                               // One gets overwritten
     * return conversations.get(id);
     * ```
     *
     * @param conversationId Unique identifier for the conversation
     * @return Existing or newly created ConversationHistory
     */
    public ConversationHistory getConversation(String conversationId) {
        return conversations.computeIfAbsent(
                conversationId,
                id -> new ConversationHistory(id)  // Lambda: only called if key missing
        );
    }

    /**
     * Adds a user message to the conversation history.
     *
     * SPRING AI MESSAGE TYPES:
     *
     * - UserMessage: What the human said
     *   → Sent to AI as: { "role": "user", "content": "..." }
     *
     * - AssistantMessage: What the AI replied
     *   → Sent to AI as: { "role": "assistant", "content": "..." }
     *
     * - SystemMessage: Instructions for the AI (not shown to user)
     *   → Sent to AI as: { "role": "system", "content": "..." }
     *
     * The Message interface is Spring AI's abstraction that gets
     * converted to the appropriate format for each AI provider.
     *
     * @param conversationId Which conversation to add the message to
     * @param content The user's message text
     */
    public void addUserMessage(String conversationId, String content) {
        ConversationHistory history = getConversation(conversationId);
        history.addMessage(new UserMessage(content));
    }

    /**
     * Adds an AI assistant message to the conversation history.
     *
     * This is called AFTER receiving a response from the AI.
     * Storing the assistant's response ensures the AI "remembers"
     * what it said in subsequent interactions.
     *
     * CONVERSATION FLOW:
     * 1. User sends message → addUserMessage()
     * 2. Send history to AI → AI generates response
     * 3. Store AI response → addAssistantMessage()
     * 4. Next user message → Repeat with updated history
     *
     * @param conversationId Which conversation to add the message to
     * @param content The AI's response text
     */
    public void addAssistantMessage(String conversationId, String content) {
        ConversationHistory history = getConversation(conversationId);
        history.addMessage(new AssistantMessage(content));
    }

    /**
     * Retrieves ALL messages in a conversation.
     *
     * USE CASES:
     * - Displaying full conversation history in UI
     * - Exporting conversation logs
     * - Admin/debugging tools
     *
     * WARNING: For very long conversations, this could return
     * thousands of messages. Consider pagination for UI display.
     *
     * @param conversationId Which conversation to retrieve
     * @return List of all messages (empty list if conversation doesn't exist)
     */
    public List<Message> getMessages(String conversationId) {
        ConversationHistory history = conversations.get(conversationId);
        return history != null ? history.getMessages() : List.of();
    }

    /**
     * Retrieves recent messages within a specified token budget.
     *
     * KEY CONCEPT: Sliding Window for Context Management
     *
     * When sending conversation history to the AI:
     * - Too few messages: AI loses important context
     * - Too many messages: Exceeds context window, increases cost/latency
     *
     * This method returns the most recent messages that fit within
     * the specified token limit, ensuring optimal context for the AI.
     *
     * EXAMPLE:
     * Conversation has 50 messages (10,000 tokens total)
     * maxTokens = 4000
     * Result: Most recent ~20 messages that fit in 4000 tokens
     *
     * @param conversationId Which conversation to retrieve from
     * @param maxTokens Maximum token budget for returned messages
     * @return List of recent messages within token limit
     */
    public List<Message> getRecentMessages(String conversationId, int maxTokens) {
        ConversationHistory history = getConversation(conversationId);
        return history.getRecentMessages(maxTokens);
    }

    /**
     * Retrieves recent messages using the default token limit (4000).
     *
     * OVERLOADED METHOD: Provides convenient default for common use case.
     * Most callers don't need to specify a custom token limit.
     *
     * @param conversationId Which conversation to retrieve from
     * @return List of recent messages within default token limit
     */
    public List<Message> getRecentMessages(String conversationId) {
        return getRecentMessages(conversationId, DEFAULT_TOKEN_LIMIT);
    }

    /**
     * Deletes a conversation and all its history.
     *
     * USE CASES:
     * - User clicks "New Conversation" button
     * - Privacy: User requests data deletion
     * - Cleanup: Removing old/inactive conversations
     *
     * NOTE: This is permanent - no undo! In production,
     * consider soft-delete or archiving for audit trails.
     *
     * @param conversationId Which conversation to delete
     */
    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
    }

    /**
     * Retrieves metadata about a conversation without returning all messages.
     *
     * USE CASES:
     * - Dashboard showing conversation stats
     * - Checking if conversation exists before operations
     * - Monitoring token usage for cost tracking
     *
     * LIGHTWEIGHT: Unlike getMessages(), this doesn't copy
     * all message content - just returns summary statistics.
     *
     * @param conversationId Which conversation to query
     * @return Map with conversation metadata, or { "exists": false } if not found
     */
    public Map<String, Object> getConversationInfo(String conversationId) {
        ConversationHistory history = conversations.get(conversationId);

        // Return early if conversation doesn't exist
        // Using get() instead of getConversation() to avoid auto-creation
        if (history == null) {
            return Map.of("exists", false);
        }

        // Return comprehensive metadata
        return Map.of(
                "exists", true,
                "conversationId", history.getConversationId(),
                "messageCount", history.getMessageCount(),
                "totalTokens", history.getTotalTokens(),
                "createdAt", history.getCreatedAt().toString(),
                "updatedAt", history.getUpdatedAt().toString()
        );
    }

    /**
     * Lists all active conversation IDs.
     *
     * USE CASES:
     * - Admin dashboard showing all conversations
     * - Cleanup jobs to find stale conversations
     * - Debugging to see what's in memory
     *
     * PRODUCTION CONSIDERATIONS:
     * - Add pagination for large numbers of conversations
     * - Filter by user ID (multi-tenant applications)
     * - Include metadata (last activity, message count) for sorting
     *
     * @return List of all conversation IDs currently stored
     */
    public List<String> listConversations() {
        return conversations.keySet().stream().toList();
    }
}
