package com.example.resource;

import com.example.model.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Index Resource - Lists all available demo endpoints
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class IndexResource {

    @GET
    public ApiResponse<Map<String, Object>> index() {
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("app", "Quarkus Extensions Demo");
        info.put("version", "1.0.0");
        info.put("quarkus_version", "3.20.1");
        info.put("built_with", "Bazel + rules_quarkus");

        // Tier 1
        Map<String, Object> tier1 = new LinkedHashMap<>();
        tier1.put("description", "REST + Jackson + Mutiny");
        tier1.put("endpoints", List.of(
            "GET  /api/users           - List all users (JSON)",
            "GET  /api/users/{id}      - Get user by ID",
            "POST /api/users           - Create user (JSON body)",
            "DELETE /api/users/{id}    - Delete user",
            "GET  /api/users/reactive  - Reactive list (Mutiny)",
            "GET  /api/users/reactive/{id} - Reactive get"
        ));
        info.put("tier_1", tier1);

        // Tier 2
        Map<String, Object> tier2 = new LinkedHashMap<>();
        tier2.put("description", "Reactive Database Clients");
        tier2.put("endpoints", List.of(
            "GET /api/db/mysql/status   - MySQL client status",
            "GET /api/db/oracle/status  - Oracle client status",
            "GET /api/db/redis/status   - Redis client status",
            "GET /api/db/redis/get/{key} - Redis GET",
            "PUT /api/db/redis/set/{key}?value=xxx - Redis SET"
        ));
        info.put("tier_2", tier2);

        // Tier 3
        Map<String, Object> tier3 = new LinkedHashMap<>();
        tier3.put("description", "Messaging (Kafka, RabbitMQ)");
        tier3.put("endpoints", List.of(
            "GET  /api/messaging/kafka/status    - Kafka status",
            "POST /api/messaging/kafka/send      - Send to Kafka",
            "GET  /api/messaging/kafka/messages  - Receive from Kafka",
            "GET  /api/messaging/rabbitmq/status - RabbitMQ status",
            "POST /api/messaging/rabbitmq/send   - Send to RabbitMQ",
            "GET  /api/messaging/rabbitmq/messages - Receive from RabbitMQ"
        ));
        info.put("tier_3", tier3);

        // Tier 4
        Map<String, Object> tier4 = new LinkedHashMap<>();
        tier4.put("description", "Observability (Prometheus, Health)");
        tier4.put("endpoints", List.of(
            "GET /api/observability - Observability info",
            "GET /q/health          - All health checks",
            "GET /q/health/live     - Liveness checks",
            "GET /q/health/ready    - Readiness checks",
            "GET /q/metrics         - Prometheus metrics"
        ));
        info.put("tier_4", tier4);

        // Tier 5
        Map<String, Object> tier5 = new LinkedHashMap<>();
        tier5.put("description", "Quarkiverse (LangChain4j AI)");
        tier5.put("endpoints", List.of(
            "GET  /api/ai/status         - LangChain4j status",
            "GET  /api/ai/providers      - Available AI providers",
            "POST /api/ai/chat           - Chat completion (mock)",
            "GET  /api/ai/example-service - Example AI service code"
        ));
        info.put("tier_5", tier5);

        return ApiResponse.success(info, "Extensions Demo - All Tiers");
    }

    @GET
    @Path("/hello")
    public String hello() {
        return "Hello from Quarkus Extensions Demo (built with Bazel)!";
    }
}
