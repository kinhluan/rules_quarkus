package com.example.resource;

import com.example.model.ApiResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tier 5 Demo: Quarkiverse LangChain4j
 *
 * This demonstrates the API structure for AI/LLM operations.
 * In production, define AI Services:
 *
 * @RegisterAiService
 * public interface Assistant {
 *     String chat(@UserMessage String message);
 * }
 *
 * Then inject and use:
 * @Inject Assistant assistant;
 * String response = assistant.chat("Hello!");
 *
 * Endpoints:
 * - GET  /api/ai/status     - LangChain4j status
 * - GET  /api/ai/providers  - Available AI providers
 * - POST /api/ai/chat       - Chat completion (mock)
 * - GET  /api/ai/models     - Available models per provider
 */
@Path("/api/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AiResource {

    private static final String TIER = "Tier 5: Quarkiverse (LangChain4j)";

    @GET
    @Path("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("extension", "quarkus-langchain4j");
        status.put("version", "0.26.1");
        status.put("configured", false);
        status.put("message", "LangChain4j available - configure API key to enable");

        Map<String, Object> quickStart = new LinkedHashMap<>();
        quickStart.put("1_add_dependency", "quarkus-langchain4j-openai (or ollama)");
        quickStart.put("2_configure", "quarkus.langchain4j.openai.api-key=sk-xxx");
        quickStart.put("3_create_service", "@RegisterAiService interface Assistant { ... }");
        quickStart.put("4_inject_and_use", "@Inject Assistant assistant;");
        status.put("quick_start", quickStart);

        return ApiResponse.success(status, TIER);
    }

    @GET
    @Path("/providers")
    public ApiResponse<Map<String, Object>> providers() {
        Map<String, Object> providers = new LinkedHashMap<>();

        // OpenAI
        Map<String, Object> openai = new LinkedHashMap<>();
        openai.put("extension", "quarkus-langchain4j-openai");
        openai.put("models", List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"));
        openai.put("config", Map.of(
            "quarkus.langchain4j.openai.api-key", "${OPENAI_API_KEY}",
            "quarkus.langchain4j.openai.chat-model.model-name", "gpt-4o-mini"
        ));
        providers.put("openai", openai);

        // Ollama (local)
        Map<String, Object> ollama = new LinkedHashMap<>();
        ollama.put("extension", "quarkus-langchain4j-ollama");
        ollama.put("models", List.of("llama3.2", "mistral", "codellama", "phi3"));
        ollama.put("config", Map.of(
            "quarkus.langchain4j.ollama.base-url", "http://localhost:11434",
            "quarkus.langchain4j.ollama.chat-model.model-id", "llama3.2"
        ));
        ollama.put("note", "Run locally with: docker run -p 11434:11434 ollama/ollama");
        providers.put("ollama", ollama);

        // Azure OpenAI
        Map<String, Object> azure = new LinkedHashMap<>();
        azure.put("extension", "quarkus-langchain4j-azure-openai");
        azure.put("config", Map.of(
            "quarkus.langchain4j.azure-openai.endpoint", "https://xxx.openai.azure.com/",
            "quarkus.langchain4j.azure-openai.api-key", "${AZURE_OPENAI_KEY}",
            "quarkus.langchain4j.azure-openai.chat-model.deployment-name", "gpt-4o"
        ));
        providers.put("azure_openai", azure);

        return ApiResponse.success(providers, TIER);
    }

    /**
     * Mock chat completion
     * In production, inject and use AI Service
     */
    @POST
    @Path("/chat")
    public Uni<ApiResponse<Map<String, Object>>> chat(Map<String, String> request) {
        return Uni.createFrom().item(() -> {
            String userMessage = request.getOrDefault("message", "Hello");
            String model = request.getOrDefault("model", "mock-model");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("user_message", userMessage);
            response.put("model", model);
            response.put("assistant_response", mockResponse(userMessage));
            response.put("tokens_used", Map.of(
                "prompt", userMessage.split(" ").length * 2,
                "completion", 50,
                "total", userMessage.split(" ").length * 2 + 50
            ));
            response.put("note", "This is a mock response. Configure an AI provider for real responses.");

            return ApiResponse.success(response, TIER + " (Chat)");
        });
    }

    @GET
    @Path("/example-service")
    public ApiResponse<Map<String, Object>> exampleService() {
        Map<String, Object> example = new LinkedHashMap<>();

        example.put("description", "Example AI Service definition");
        example.put("code", """
            import dev.langchain4j.service.SystemMessage;
            import dev.langchain4j.service.UserMessage;
            import io.quarkiverse.langchain4j.RegisterAiService;

            @RegisterAiService
            public interface Assistant {

                @SystemMessage("You are a helpful assistant.")
                String chat(@UserMessage String message);

                @SystemMessage("You are a code reviewer. Review the following code.")
                String reviewCode(@UserMessage String code);

                @SystemMessage("Translate the following text to {language}.")
                String translate(@UserMessage String text, String language);
            }
            """);

        example.put("usage", """
            @Inject
            Assistant assistant;

            public void example() {
                String response = assistant.chat("What is Quarkus?");
                String review = assistant.reviewCode("public void foo() { }");
                String translated = assistant.translate("Hello", "Vietnamese");
            }
            """);

        return ApiResponse.success(example, TIER);
    }

    private String mockResponse(String userMessage) {
        if (userMessage.toLowerCase().contains("hello")) {
            return "Hello! I'm a mock AI assistant. Configure quarkus-langchain4j with a real provider (OpenAI, Ollama, etc.) for actual responses.";
        } else if (userMessage.toLowerCase().contains("quarkus")) {
            return "Quarkus is a Kubernetes-native Java framework designed for GraalVM and HotSpot. This demo shows how to integrate LangChain4j with Quarkus!";
        } else {
            return "This is a mock response. Your message was: \"" + userMessage + "\". To get real AI responses, configure an AI provider.";
        }
    }
}
