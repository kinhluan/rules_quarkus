package com.example.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Tier 4 Demo: Custom Health Check for Database
 *
 * This health check is exposed at:
 * - GET /q/health/ready
 * - GET /q/health
 */
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        // In production, actually check database connection
        // Example: mysqlClient.query("SELECT 1").execute()

        return HealthCheckResponse.named("Database connection")
                .withData("mysql", "not configured")
                .withData("oracle", "not configured")
                .withData("redis", "not configured")
                .withData("tier", "Tier 4: Observability")
                .up()
                .build();
    }
}
