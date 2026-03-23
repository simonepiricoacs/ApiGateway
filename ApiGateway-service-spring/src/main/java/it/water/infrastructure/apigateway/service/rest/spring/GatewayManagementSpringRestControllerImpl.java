package it.water.infrastructure.apigateway.service.rest.spring;

import it.water.infrastructure.apigateway.model.ServiceStats;
import it.water.infrastructure.apigateway.service.rest.GatewayManagementRestControllerImpl;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Spring REST controller for Gateway Management.
 */
@RestController
public class GatewayManagementSpringRestControllerImpl extends GatewayManagementRestControllerImpl implements GatewayManagementSpringRestApi {

    @Override
    @SuppressWarnings("java:S1185")
    public Map<String, Object> health() {
        return super.health();
    }

    @Override
    @SuppressWarnings("java:S1185")
    public Map<String, ServiceStats> metrics() {
        return super.metrics();
    }

    @Override
    @SuppressWarnings("java:S1185")
    public Map<String, String> circuitBreakers() {
        return super.circuitBreakers();
    }

    @Override
    @SuppressWarnings("java:S1185")
    public void syncServiceDiscovery() {
        super.syncServiceDiscovery();
    }
}
