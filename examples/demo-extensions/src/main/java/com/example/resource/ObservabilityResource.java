package com.example.resource;

import com.example.model.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tier 4 Demo: Observability Info
 *
 * Provides information about available observability endpoints.
 *
 * Built-in Quarkus endpoints (when extensions are enabled):
 * - GET /q/health       - All health checks
 * - GET /q/health/live  - Liveness checks
 * - GET /q/health/ready - Readiness checks
 * - GET /q/metrics      - Prometheus metrics
 */
@Path("/api/observability")
@Produces(MediaType.APPLICATION_JSON)
public class ObservabilityResource {

    private static final String TIER = "Tier 4: Observability (Prometheus, Health)";

    @GET
    public ApiResponse<Map<String, Object>> getObservabilityInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        // Health endpoints
        Map<String, String> health = new LinkedHashMap<>();
        health.put("/q/health", "All health checks (liveness + readiness)");
        health.put("/q/health/live", "Liveness checks - is the app running?");
        health.put("/q/health/ready", "Readiness checks - can app serve traffic?");
        health.put("/q/health/started", "Startup checks");
        info.put("health_endpoints", health);

        // Metrics endpoints
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("/q/metrics", "Prometheus metrics (all)");
        metrics.put("/q/metrics/application", "Application-specific metrics");
        metrics.put("/q/metrics/base", "Base JVM metrics");
        metrics.put("/q/metrics/vendor", "Vendor-specific metrics");
        info.put("metrics_endpoints", metrics);

        // Custom health checks in this app
        Map<String, String> customChecks = new LinkedHashMap<>();
        customChecks.put("ApplicationHealthCheck", "Liveness - JVM uptime, memory usage");
        customChecks.put("DatabaseHealthCheck", "Readiness - Database connection status");
        info.put("custom_health_checks", customChecks);

        // Prometheus config example
        Map<String, Object> prometheusConfig = new LinkedHashMap<>();
        prometheusConfig.put("scrape_config", Map.of(
            "job_name", "quarkus-app",
            "metrics_path", "/q/metrics",
            "static_configs", Map.of("targets", "localhost:8080")
        ));
        info.put("prometheus_example", prometheusConfig);

        return ApiResponse.success(info, TIER);
    }
}
