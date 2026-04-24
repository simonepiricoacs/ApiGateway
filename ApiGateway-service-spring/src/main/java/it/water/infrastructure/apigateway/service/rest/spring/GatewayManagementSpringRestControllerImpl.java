package it.water.infrastructure.apigateway.service.rest.spring;

import it.water.infrastructure.apigateway.api.GatewaySystemApi;
import it.water.infrastructure.apigateway.model.ServiceStats;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring REST controller for Gateway Management.
 */
@RestController
public class GatewayManagementSpringRestControllerImpl implements GatewayManagementSpringRestApi {

    private final GatewaySystemApi gatewaySystemApi;

    public GatewayManagementSpringRestControllerImpl(GatewaySystemApi gatewaySystemApi) {
        this.gatewaySystemApi = gatewaySystemApi;
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "ApiGateway");
        return health;
    }

    @Override
    public Map<String, ServiceStats> metrics() {
        return gatewaySystemApi.getServiceStatistics();
    }

    @Override
    public Map<String, String> circuitBreakers() {
        Map<String, ServiceStats> stats = gatewaySystemApi.getServiceStatistics();
        Map<String, String> result = new HashMap<>();
        stats.forEach((service, stat) -> result.put(service,
                stat.getCircuitState() != null ? stat.getCircuitState().name() : "CLOSED"));
        return result;
    }

    @Override
    public ResponseEntity<Map<String, String>> syncServiceDiscovery() {
        try {
            gatewaySystemApi.syncWithServiceDiscovery();
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage() == null ? "" : e.getMessage()));
        }
    }
}
