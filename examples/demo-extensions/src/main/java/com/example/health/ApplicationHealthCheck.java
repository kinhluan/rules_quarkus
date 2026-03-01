package com.example.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;

/**
 * Tier 4 Demo: Custom Liveness Health Check
 *
 * This health check is exposed at:
 * - GET /q/health/live
 * - GET /q/health
 */
@Liveness
@ApplicationScoped
public class ApplicationHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        long uptimeSeconds = runtime.getUptime() / 1000;
        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);

        return HealthCheckResponse.named("Application status")
                .withData("uptime_seconds", uptimeSeconds)
                .withData("heap_used_mb", heapUsed)
                .withData("heap_max_mb", heapMax)
                .withData("java_version", System.getProperty("java.version"))
                .withData("tier", "Tier 4: Observability")
                .up()
                .build();
    }
}
