package it.water.infrastructure.apigateway.service.rest.spring;

import it.water.infrastructure.apigateway.api.rest.GatewayManagementRestApi;
import it.water.infrastructure.apigateway.model.ServiceStats;
import it.water.core.api.service.rest.FrameworkRestApi;
import it.water.service.rest.api.security.LoggedIn;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Spring REST API interface for Gateway Management.
 */
@RequestMapping("/api/gateway/management")
@FrameworkRestApi
public interface GatewayManagementSpringRestApi extends GatewayManagementRestApi {

    @LoggedIn
    @GetMapping("/health")
    Map<String, Object> health();

    @LoggedIn
    @GetMapping("/metrics")
    Map<String, ServiceStats> metrics();

    @LoggedIn
    @GetMapping("/circuit-breakers")
    Map<String, String> circuitBreakers();

    @LoggedIn
    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void syncServiceDiscovery();
}
