package com.coder.ai_chatbot.service;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Service for creating structured code review prompts using templates.
 *
 * KEY CONCEPT: Prompt Engineering with Templates
 *
 * Why use templates instead of string concatenation?
 *
 * 1. MAINTAINABILITY:
 *    - Templates live in separate files (easy to edit without recompiling)
 *    - Non-developers can tweak prompts without touching Java code
 *    - Version control shows prompt changes clearly
 *
 * 2. READABILITY:
 *    - Complex prompts with instructions, examples, and formatting
 *      are much cleaner in a dedicated file
 *    - No messy string concatenation or escape characters
 *
 * 3. REUSABILITY:
 *    - Same template can be used with different variable values
 *    - Easy to create variations (detailed review, quick review, etc.)
 *
 * 4. TESTABILITY:
 *    - Templates can be tested independently
 *    - Easy to A/B test different prompt versions
 *
 * PROMPT TEMPLATE SYNTAX:
 * Templates use {variableName} placeholders that get replaced at runtime.
 * Example: "Review this {language} code: {code}"
 *
 * @author HungryCoders
 */
@Service
public class CodeReviewService {

    /**
     * Spring's ResourceLoader for accessing files from the classpath.
     *
     * ResourceLoader is a Spring abstraction that works across different
     * environments (JAR files, filesystem, URLs, etc.) without changing code.
     *
     * Common resource prefixes:
     * - "classpath:" → Files in src/main/resources (packaged in JAR)
     * - "file:" → Absolute filesystem path
     * - "http:" → Remote URL
     */
    private final ResourceLoader resourceLoader;

    /**
     * Constructor injection (preferred over @Autowired on fields).
     *
     * WHY CONSTRUCTOR INJECTION?
     * - Makes dependencies explicit and required
     * - Easier to test (just pass mocks in constructor)
     * - Ensures immutability (fields can be final)
     * - Fails fast if dependency is missing
     *
     * @param resourceLoader Injected by Spring automatically
     */
    public CodeReviewService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Creates a structured Prompt for AI code review.
     *
     * This method demonstrates the TEMPLATE PATTERN for prompt engineering:
     * 1. Load a pre-written template from resources
     * 2. Fill in dynamic variables (code, language, requirements)
     * 3. Return a Prompt object ready to send to the AI
     *
     * SPRING AI PROMPT WORKFLOW:
     *
     *    Template File                Variables Map              Prompt Object
     *    ─────────────                ─────────────              ─────────────
     *    "Review this                 { "language": "Java",  →   Complete prompt
     *    {language} code:              "code": "...",            ready for AI
     *    {code}                        "businessRequirements":    API call
     *    Requirements:                 "..." }
     *    {businessRequirements}"
     *
     * EXAMPLE:
     * Template: "Review this {language} code: {code}"
     * Variables: { "language": "Java", "code": "public void test() {}" }
     * Result: "Review this Java code: public void test() {}"
     *
     * @param code                 The source code to review
     * @param language             Programming language (Java, Python, etc.)
     * @param businessRequirements Optional context about what the code should do
     * @return Prompt object ready to be sent to AI
     */
    public Prompt createCodeReviewPrompt(String code, String language, String businessRequirements) {
        // ==================== STEP 1: LOAD TEMPLATE ====================
        // Template is stored in src/main/resources/templates/code-review.txt
        // Keeping prompts in files allows easy editing without redeployment
        String templateContent = loadTemplate();

        // ==================== STEP 2: CREATE PROMPT TEMPLATE ====================
        // PromptTemplate is Spring AI's class for handling variable substitution.
        // It parses the template and identifies {placeholders} for replacement.
        PromptTemplate promptTemplate = new PromptTemplate(templateContent);

        // ==================== STEP 3: PREPARE VARIABLES ====================
        // Map.of() creates an immutable map of variable names → values
        //
        // NOTE: The keys ("code", "language", "businessRequirements") MUST match
        // the placeholder names in the template file exactly!
        //
        // DEFENSIVE CODING: We provide a sensible default if businessRequirements
        // is null or empty. This prevents the AI from getting confused by empty
        // placeholders and gives it clear instructions.
        Map<String, Object> variables = Map.of(
                "code", code,
                "language", language,
                "businessRequirements", businessRequirements != null
                        && !businessRequirements.trim().isEmpty()
                        ? businessRequirements
                        : "No specific business requirements provided. Review technical quality only."
        );

        // ==================== STEP 4: CREATE AND RETURN PROMPT ====================
        // promptTemplate.create(variables) performs the substitution and
        // returns a Prompt object that can be passed to ChatClient.
        //
        // The Prompt object wraps the filled-in template text and can include
        // additional metadata like chat options, system messages, etc.
        return promptTemplate.create(variables);
    }

    /**
     * Loads the code review template from the classpath.
     *
     * TEMPLATE LOCATION: src/main/resources/templates/code-review.txt
     *
     * After building, this becomes: classpath:templates/code-review.txt
     * (packaged inside the JAR file)
     *
     * EXAMPLE TEMPLATE CONTENT (code-review.txt):
     * ─────────────────────────────────────────────
     * You are an expert code reviewer. Review the following {language} code.
     *
     * CODE TO REVIEW:
     * ```{language}
     * {code}
     * ```
     *
     * BUSINESS REQUIREMENTS:
     * {businessRequirements}
     *
     * Please provide:
     * 1. Code quality assessment
     * 2. Potential bugs or issues
     * 3. Performance considerations
     * 4. Best practice recommendations
     * 5. Security concerns (if any)
     * ─────────────────────────────────────────────
     *
     * @return The template content as a String
     * @throws RuntimeException if template file cannot be loaded
     */
    private String loadTemplate() {
        try {
            // Load resource from classpath (works in JAR and IDE)
            Resource resource = resourceLoader.getResource(
                    "classpath:templates/code-review.txt"
            );
            // Read entire file content as UTF-8 string
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Fail fast with clear error message
            // In production, consider custom exception for better error handling
            throw new RuntimeException("Failed to load code review template", e);
        }
    }
}
