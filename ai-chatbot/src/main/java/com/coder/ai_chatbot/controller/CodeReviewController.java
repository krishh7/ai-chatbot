package com.coder.ai_chatbot.controller;

import com.coder.ai_chatbot.service.CodeReviewService;
import com.coder.ai_chatbot.service.ModelService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST Controller for AI-powered code review functionality.
 *
 * This controller demonstrates a practical use case of Spring AI:
 * Automated code review using LLMs. It combines:
 * - Template-based prompt engineering (via CodeReviewService)
 * - Multi-provider AI support (OpenAI, Gemini, etc.)
 * - Structured JSON responses with metadata
 *
 * USE CASE: Developers submit code snippets and receive AI-generated
 * reviews covering code quality, best practices, potential bugs,
 * and alignment with business requirements.
 *
 * @author HungryCoders
 */
@RestController
@RequestMapping("/api/code-review")
public class CodeReviewController {

    /**
     * Service for resolving the appropriate ChatClient based on provider name.
     * Reuses the same pattern as ChatController for consistency.
     */
    @Autowired
    private ModelService modelService;

    /**
     * Service responsible for creating structured prompts for code review.
     * Demonstrates separation of concerns: prompt engineering logic
     * is extracted into a dedicated service.
     */
    @Autowired
    private CodeReviewService codeReviewService;

    /**
     * Endpoint to submit code for AI-powered review.
     *
     * ENDPOINT: POST /api/code-review
     *
     * REQUEST BODY (JSON):
     * {
     *     "code": "public class Example { ... }",           // Required: Code to review
     *     "language": "Java",                               // Optional: Programming language (defaults to "Java")
     *     "businessRequirements": "Must handle null inputs" // Optional: Context for the review
     * }
     *
     * QUERY PARAMETERS:
     * - provider (optional): AI provider to use. Defaults to "openai"
     * - model (optional): Specific model to use. Defaults to "gpt-5"
     *
     * RESPONSE (JSON):
     * {
     *     "success": true,
     *     "language": "Java",
     *     "provider": "openai",
     *     "model": "gpt-5",
     *     "review": "... AI generated review ...",
     *     "codeLength": 150,
     *     "hasBusinessRequirements": true
     * }
     *
     * EXAMPLE USAGE:
     * curl -X POST "http://localhost:8081/api/code-review?provider=openai&model=gpt-4o" \
     *      -H "Content-Type: application/json" \
     *      -d '{
     *            "code": "public void process(String s) { System.out.println(s.length()); }",
     *            "language": "Java",
     *            "businessRequirements": "Should handle null strings gracefully"
     *          }'
     *
     * @param request JSON body containing code, language, and optional business requirements
     * @param provider AI provider to use (query param, defaults to "openai")
     * @param model Specific AI model to use (query param, defaults to "gpt-5")
     * @return Map containing review results and metadata
     */
    @PostMapping
    public Map<String, Object> reviewCode(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "open-ai") String provider,
            @RequestParam(defaultValue = "gpt-5") String model
    ) {
        // ==================== STEP 1: EXTRACT INPUT DATA ====================
        // Using Map<String, String> for flexibility - no need for a dedicated DTO
        // getOrDefault() provides sensible defaults for optional fields
        String code = request.get("code");
        String language = request.getOrDefault("language", "Java");
        String businessRequirements = request.get("businessRequirements");

        // ==================== STEP 2: INPUT VALIDATION ====================
        // Early return pattern: fail fast if required data is missing
        // Returns a structured error response instead of throwing exceptions
        if (code == null || code.trim().isEmpty()) {
            return Map.of(
                    "error", true,
                    "message", "Code cannot be empty"
            );
        }

        // Validate: For non-OpenAI providers, model is mandatory
        if (!"open-ai".equalsIgnoreCase(provider) &&
                (model == null || model.isEmpty())) {

            return Map.of(
                    "error", true,
                    "message", "AI-Model header is required when using provider: " + provider
            );
        }

        // ==================== STEP 3: CREATE STRUCTURED PROMPT ====================
        // KEY CONCEPT: Prompt Engineering
        //
        // The Prompt class in Spring AI represents a structured prompt that can include:
        // - System messages (instructions for the AI)
        // - User messages (the actual query/content)
        // - Chat history (for multi-turn conversations)
        //
        // CodeReviewService.createCodeReviewPrompt() uses a PROMPT TEMPLATE
        // (likely from src/main/resources/prompts/) to create a well-structured
        // prompt with placeholders filled in dynamically.
        //
        // This separation keeps prompt templates maintainable and testable.
        Prompt prompt = codeReviewService.createCodeReviewPrompt(code, language, businessRequirements);

        // ==================== STEP 4: GET AI CLIENT ====================
        // Same pattern as ChatController - provider-based routing
        ChatClient chatClient = modelService.getChatClient(provider);

        // ==================== STEP 5: EXECUTE AI REQUEST ====================
        // KEY CONCEPT: prompt.getContents()
        //
        // The Prompt object contains structured messages, but the ChatClient's
        // .user() method expects a String. getContents() extracts the text
        // content from the prompt for use in the fluent API.
        //
        // NOTE: In more advanced scenarios, you might use:
        // chatClient.prompt(prompt).call()
        // to pass the entire Prompt object directly, preserving system messages.
        String review = chatClient.prompt()
                .user(prompt.getContents())
                .options(OpenAiChatOptions.builder()
                        .model(model)    // Use the model specified in query param
                        .build())
                .call()
                .content();

        // ==================== STEP 6: BUILD RESPONSE ====================
        // Return a rich response with metadata for debugging and transparency
        // Map.of() creates an immutable map - clean and concise for responses
        return Map.of(
                "success", true,
                "language", language,
                "provider", provider,
                "model", model,
                "review", review,
                "codeLength", code.length(),                                                    // Useful metadata for analytics
                "hasBusinessRequirements", businessRequirements != null && !businessRequirements.trim().isEmpty()  // Indicates if context was provided
        );
    }

    /**
     * Simple health check endpoint for monitoring and load balancers.
     *
     * ENDPOINT: GET /api/code-review/health
     *
     * Common pattern in microservices for:
     * - Kubernetes liveness/readiness probes
     * - Load balancer health checks
     * - Service discovery health verification
     *
     * @return Map with status and service name
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "healthy",
                "service", "Code Review Service"
        );
    }
}
