package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.CircuitBreakerConfig;
import it.water.infrastructure.apigateway.model.CircuitState;
import it.water.core.api.service.Service;

/**
 * Circuit breaker API - protects upstream services from cascading failures.
 */
public interface CircuitBreakerApi extends Service {
    boolean allowRequest(String serviceName, String instanceId);
    void recordSuccess(String serviceName, String instanceId);
    void recordFailure(String serviceName, String instanceId);
    CircuitState getState(String serviceName, String instanceId);
    CircuitBreakerConfig getConfig(String serviceName);
    void configure(String serviceName, CircuitBreakerConfig config);
}
