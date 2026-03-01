package com.example.resource;

import com.example.model.ApiResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * Tier 2 Demo: Reactive Database Clients
 *
 * This demonstrates the API structure for reactive database operations.
 * In production, inject actual clients:
 * - @Inject io.vertx.mutiny.mysqlclient.MySQLPool mysqlClient;
 * - @Inject io.vertx.mutiny.oracleclient.OraclePool oracleClient;
 * - @Inject io.quarkus.redis.datasource.ReactiveRedisDataSource redis;
 *
 * Endpoints:
 * - GET /api/db/mysql/status   - MySQL connection status
 * - GET /api/db/oracle/status  - Oracle connection status
 * - GET /api/db/redis/status   - Redis connection status
 * - GET /api/db/redis/get/{key} - Redis GET demo
 * - PUT /api/db/redis/set/{key} - Redis SET demo
 */
@Path("/api/db")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DatabaseResource {

    private static final String TIER = "Tier 2: Reactive Database Clients";

    // In-memory cache simulating Redis
    private static final Map<String, String> redisCache = new HashMap<>();

    /**
     * MySQL connection status (mock)
     * In production: mysqlClient.query("SELECT 1").execute()
     */
    @GET
    @Path("/mysql/status")
    public Uni<ApiResponse<Map<String, Object>>> mysqlStatus() {
        return Uni.createFrom().item(() -> {
            Map<String, Object> status = new HashMap<>();
            status.put("client", "io.vertx.mutiny.mysqlclient.MySQLPool");
            status.put("connected", false);
            status.put("message", "MySQL client available - configure quarkus.datasource.reactive.url to connect");
            status.put("example_config", Map.of(
                "quarkus.datasource.reactive.url", "mysql://localhost:3306/mydb",
                "quarkus.datasource.username", "user",
                "quarkus.datasource.password", "password"
            ));
            return ApiResponse.success(status, TIER);
        });
    }

    /**
     * Oracle connection status (mock)
     * In production: oracleClient.query("SELECT 1 FROM DUAL").execute()
     */
    @GET
    @Path("/oracle/status")
    public Uni<ApiResponse<Map<String, Object>>> oracleStatus() {
        return Uni.createFrom().item(() -> {
            Map<String, Object> status = new HashMap<>();
            status.put("client", "io.vertx.mutiny.oracleclient.OraclePool");
            status.put("connected", false);
            status.put("message", "Oracle client available - configure quarkus.datasource.reactive.url to connect");
            status.put("example_config", Map.of(
                "quarkus.datasource.reactive.url", "oracle:thin:@localhost:1521/FREEPDB1",
                "quarkus.datasource.username", "user",
                "quarkus.datasource.password", "password"
            ));
            return ApiResponse.success(status, TIER);
        });
    }

    /**
     * Redis connection status (mock)
     * In production: redis.value(String.class).get("test")
     */
    @GET
    @Path("/redis/status")
    public Uni<ApiResponse<Map<String, Object>>> redisStatus() {
        return Uni.createFrom().item(() -> {
            Map<String, Object> status = new HashMap<>();
            status.put("client", "io.quarkus.redis.datasource.ReactiveRedisDataSource");
            status.put("connected", false);
            status.put("cached_keys", redisCache.size());
            status.put("message", "Redis client available - configure quarkus.redis.hosts to connect");
            status.put("example_config", Map.of(
                "quarkus.redis.hosts", "redis://localhost:6379"
            ));
            return ApiResponse.success(status, TIER);
        });
    }

    /**
     * Redis GET operation (mock using in-memory map)
     */
    @GET
    @Path("/redis/get/{key}")
    public Uni<ApiResponse<Map<String, String>>> redisGet(@PathParam("key") String key) {
        return Uni.createFrom().item(() -> {
            String value = redisCache.get(key);
            Map<String, String> result = new HashMap<>();
            result.put("key", key);
            result.put("value", value);
            result.put("found", String.valueOf(value != null));
            return ApiResponse.success(result, TIER + " (Redis GET)");
        });
    }

    /**
     * Redis SET operation (mock using in-memory map)
     */
    @PUT
    @Path("/redis/set/{key}")
    public Uni<ApiResponse<Map<String, String>>> redisSet(
            @PathParam("key") String key,
            @QueryParam("value") String value) {
        return Uni.createFrom().item(() -> {
            redisCache.put(key, value);
            Map<String, String> result = new HashMap<>();
            result.put("key", key);
            result.put("value", value);
            result.put("operation", "SET");
            return ApiResponse.success(result, TIER + " (Redis SET)");
        });
    }
}
