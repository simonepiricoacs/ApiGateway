package it.water.infrastructure.apigateway.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime-agnostic helper for the Gateway management endpoints.
 * <p>
 * Both the JAX-RS and the Spring MVC management controllers are intentionally kept
 * as separate runtime adapters, but the payload-building logic they expose (health
 * status, circuit-breaker states) is identical and lives here so it is authored once.
 */
public final class GatewayManagementSupport {

    private GatewayManagementSupport() {
    }

    /**
     * Builds the health payload returned by the {@code /management/health} endpoint.
     */
    public static Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "ApiGateway");
        return health;
    }

    /**
     * Maps the per-service statistics to their circuit-breaker state name,
     * defaulting to {@link CircuitState#CLOSED} when no state is available.
     */
    public static Map<String, String> circuitBreakers(Map<String, ServiceStats> stats) {
        Map<String, String> result = new HashMap<>();
        stats.forEach((service, stat) -> {
            CircuitState state = stat.getCircuitState();
            result.put(service, state != null ? state.name() : CircuitState.CLOSED.name());
        });
        return result;
    }
}
