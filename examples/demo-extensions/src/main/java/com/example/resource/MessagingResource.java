package com.example.resource;

import com.example.model.ApiResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tier 3 Demo: Messaging (Kafka, RabbitMQ)
 *
 * This demonstrates the API structure for messaging operations.
 * In production, use SmallRye Reactive Messaging:
 *
 * @Incoming("orders-in")
 * public void processOrder(Order order) { ... }
 *
 * @Outgoing("orders-out")
 * public Multi<Order> generateOrders() { ... }
 *
 * @Channel("orders") Emitter<Order> emitter;
 *
 * Endpoints:
 * - GET  /api/messaging/kafka/status    - Kafka status
 * - POST /api/messaging/kafka/send      - Send message to Kafka topic
 * - GET  /api/messaging/kafka/messages  - Get received messages
 * - GET  /api/messaging/rabbitmq/status - RabbitMQ status
 * - POST /api/messaging/rabbitmq/send   - Send message to RabbitMQ queue
 */
@Path("/api/messaging")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessagingResource {

    private static final String TIER = "Tier 3: Messaging (Kafka, RabbitMQ)";

    // In-memory message queues (simulating message brokers)
    private static final Queue<Map<String, Object>> kafkaMessages = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> rabbitmqMessages = new ConcurrentLinkedQueue<>();

    /**
     * Kafka connection status
     */
    @GET
    @Path("/kafka/status")
    public Uni<ApiResponse<Map<String, Object>>> kafkaStatus() {
        return Uni.createFrom().item(() -> {
            Map<String, Object> status = new HashMap<>();
            status.put("broker", "Apache Kafka");
            status.put("extension", "quarkus-messaging-kafka");
            status.put("connected", false);
            status.put("pending_messages", kafkaMessages.size());
            status.put("message", "Kafka available - configure kafka.bootstrap.servers to connect");
            status.put("example_config", Map.of(
                "kafka.bootstrap.servers", "localhost:9092",
                "mp.messaging.incoming.orders.connector", "smallrye-kafka",
                "mp.messaging.incoming.orders.topic", "orders",
                "mp.messaging.outgoing.orders-out.connector", "smallrye-kafka",
                "mp.messaging.outgoing.orders-out.topic", "orders-processed"
            ));
            return ApiResponse.success(status, TIER);
        });
    }

    /**
     * Send message to Kafka (mock)
     */
    @POST
    @Path("/kafka/send")
    public Uni<ApiResponse<Map<String, Object>>> kafkaSend(Map<String, Object> message) {
        return Uni.createFrom().item(() -> {
            Map<String, Object> enriched = new HashMap<>(message);
            enriched.put("_id", UUID.randomUUID().toString());
            enriched.put("_timestamp", Instant.now().toString());
            enriched.put("_broker", "kafka");
            kafkaMessages.offer(enriched);

            Map<String, Object> result = new HashMap<>();
            result.put("sent", true);
            result.put("message_id", enriched.get("_id"));
            result.put("topic", "demo-topic");
            result.put("queue_size", kafkaMessages.size());
            return ApiResponse.success(result, TIER + " (Kafka Producer)");
        });
    }

    /**
     * Get Kafka messages (mock consumer)
     */
    @GET
    @Path("/kafka/messages")
    public Uni<ApiResponse<List<Map<String, Object>>>> kafkaMessages(
            @QueryParam("limit") @DefaultValue("10") int limit) {
        return Uni.createFrom().item(() -> {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (int i = 0; i < limit && !kafkaMessages.isEmpty(); i++) {
                Map<String, Object> msg = kafkaMessages.poll();
                if (msg != null) messages.add(msg);
            }
            return ApiResponse.success(messages, TIER + " (Kafka Consumer)");
        });
    }

    /**
     * RabbitMQ connection status
     */
    @GET
    @Path("/rabbitmq/status")
    public Uni<ApiResponse<Map<String, Object>>> rabbitmqStatus() {
        return Uni.createFrom().item(() -> {
            Map<String, Object> status = new HashMap<>();
            status.put("broker", "RabbitMQ");
            status.put("extension", "quarkus-messaging-rabbitmq");
            status.put("connected", false);
            status.put("pending_messages", rabbitmqMessages.size());
            status.put("message", "RabbitMQ available - configure rabbitmq-host to connect");
            status.put("example_config", Map.of(
                "rabbitmq-host", "localhost",
                "rabbitmq-port", 5672,
                "rabbitmq-username", "guest",
                "rabbitmq-password", "guest",
                "mp.messaging.incoming.tasks.connector", "smallrye-rabbitmq",
                "mp.messaging.incoming.tasks.queue.name", "tasks"
            ));
            return ApiResponse.success(status, TIER);
        });
    }

    /**
     * Send message to RabbitMQ (mock)
     */
    @POST
    @Path("/rabbitmq/send")
    public Uni<ApiResponse<Map<String, Object>>> rabbitmqSend(Map<String, Object> message) {
        return Uni.createFrom().item(() -> {
            Map<String, Object> enriched = new HashMap<>(message);
            enriched.put("_id", UUID.randomUUID().toString());
            enriched.put("_timestamp", Instant.now().toString());
            enriched.put("_broker", "rabbitmq");
            rabbitmqMessages.offer(enriched);

            Map<String, Object> result = new HashMap<>();
            result.put("sent", true);
            result.put("message_id", enriched.get("_id"));
            result.put("queue", "demo-queue");
            result.put("queue_size", rabbitmqMessages.size());
            return ApiResponse.success(result, TIER + " (RabbitMQ Producer)");
        });
    }

    /**
     * Get RabbitMQ messages (mock consumer)
     */
    @GET
    @Path("/rabbitmq/messages")
    public Uni<ApiResponse<List<Map<String, Object>>>> rabbitmqMessages(
            @QueryParam("limit") @DefaultValue("10") int limit) {
        return Uni.createFrom().item(() -> {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (int i = 0; i < limit && !rabbitmqMessages.isEmpty(); i++) {
                Map<String, Object> msg = rabbitmqMessages.poll();
                if (msg != null) messages.add(msg);
            }
            return ApiResponse.success(messages, TIER + " (RabbitMQ Consumer)");
        });
    }
}
