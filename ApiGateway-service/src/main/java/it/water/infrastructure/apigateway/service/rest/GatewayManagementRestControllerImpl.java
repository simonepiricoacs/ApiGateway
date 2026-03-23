package it.water.infrastructure.apigateway.service.rest;

import it.water.infrastructure.apigateway.api.GatewaySystemApi;
import it.water.infrastructure.apigateway.api.rest.GatewayManagementRestApi;
import it.water.infrastructure.apigateway.model.CircuitState;
import it.water.infrastructure.apigateway.model.ServiceStats;
import it.water.core.api.service.rest.FrameworkRestController;
import it.water.core.interceptors.annotations.Inject;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for gateway management operations.
 */
@FrameworkRestController(referredRestApi = GatewayManagementRestApi.class)
public class GatewayManagementRestControllerImpl implements GatewayManagementRestApi {

    private static final Logger log = LoggerFactory.getLogger(GatewayManagementRestControllerImpl.class);

    @Inject
    @Setter
    private GatewaySystemApi gatewaySystemApi;

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
        stats.forEach((service, stat) -> {
            CircuitState state = stat.getCircuitState();
            result.put(service, state != null ? state.name() : CircuitState.CLOSED.name());
        });
        return result;
    }

    @Override
    public void syncServiceDiscovery() {
        log.info("REST: triggering ServiceDiscovery sync");
        gatewaySystemApi.syncWithServiceDiscovery();
    }
}
