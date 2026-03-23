package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.Route;
import it.water.infrastructure.apigateway.model.ServiceStats;
import it.water.core.api.service.BaseEntitySystemApi;
import it.water.service.discovery.model.ServiceRegistration;

import java.util.List;
import java.util.Map;

/**
 * Gateway system API - internal operations for service discovery integration and management.
 */
public interface GatewaySystemApi extends BaseEntitySystemApi<Route> {
    void syncWithServiceDiscovery();
    void evictServiceFromCache(String serviceName);
    Map<String, ServiceStats> getServiceStatistics();
    List<ServiceRegistration> getHealthyInstances(String serviceName);
}
