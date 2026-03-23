package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.GatewayRequest;
import it.water.infrastructure.apigateway.model.LoadBalancerStrategy;
import it.water.core.api.service.Service;
import it.water.service.discovery.model.ServiceRegistration;

import java.util.List;

/**
 * Load balancer API - selects upstream service instances.
 */
public interface LoadBalancerApi extends Service {
    ServiceRegistration selectInstance(String serviceName, GatewayRequest request, List<ServiceRegistration> instances);
    void reportSuccess(String serviceName, String instanceId, long latencyMs);
    void reportFailure(String serviceName, String instanceId, Exception error);
    LoadBalancerStrategy getStrategy();
    void setStrategy(LoadBalancerStrategy strategy);
}
