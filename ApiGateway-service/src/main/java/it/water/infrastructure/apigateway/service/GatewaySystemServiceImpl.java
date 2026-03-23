package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.*;
import it.water.infrastructure.apigateway.model.*;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.repository.service.BaseEntitySystemServiceImpl;
import it.water.service.discovery.api.ServiceRegistrationApi;
import it.water.service.discovery.model.ServiceRegistration;
import it.water.service.discovery.model.ServiceStatus;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway system service - handles service discovery integration and gateway management.
 */
@FrameworkComponent
public class GatewaySystemServiceImpl extends BaseEntitySystemServiceImpl<Route> implements GatewaySystemApi {

    private static final Logger log = LoggerFactory.getLogger(GatewaySystemServiceImpl.class);

    @Inject
    @Getter
    @Setter
    private RouteRepository repository;

    @Inject
    @Getter
    @Setter
    private CircuitBreakerApi circuitBreakerApi;

    @Inject
    @Getter
    @Setter
    private ServiceRegistrationApi serviceRegistrationApi;

    private final Map<String, List<ServiceRegistration>> serviceCache = new ConcurrentHashMap<>();
    private final Map<String, ServiceStats> statsMap = new ConcurrentHashMap<>();

    public GatewaySystemServiceImpl() {
        super(Route.class);
    }

    @Override
    public void syncWithServiceDiscovery() {
        log.info("Syncing with ServiceDiscovery");
        try {
            List<ServiceRegistration> services = serviceRegistrationApi.getAvailableServices();
            serviceCache.clear();
            for (ServiceRegistration reg : services) {
                serviceCache.computeIfAbsent(reg.getServiceName(), k -> new ArrayList<>()).add(reg);
            }
            log.info("Synced {} service instances from ServiceDiscovery", services.size());
        } catch (Exception e) {
            log.warn("Failed to sync with ServiceDiscovery: {}", e.getMessage());
        }
    }

    @Override
    public void evictServiceFromCache(String serviceName) {
        log.info("Evicting service from cache: {}", serviceName);
        serviceCache.remove(serviceName);
    }

    @Override
    public Map<String, ServiceStats> getServiceStatistics() {
        return Collections.unmodifiableMap(statsMap);
    }

    @Override
    public List<ServiceRegistration> getHealthyInstances(String serviceName) {
        List<ServiceRegistration> instances = serviceCache.getOrDefault(serviceName, Collections.emptyList());
        List<ServiceRegistration> healthy = new ArrayList<>();
        for (ServiceRegistration reg : instances) {
            if (reg.getStatus() == ServiceStatus.UP) {
                CircuitState state = circuitBreakerApi.getState(serviceName, reg.getInstanceId());
                if (state == CircuitState.CLOSED || state == CircuitState.HALF_OPEN) {
                    healthy.add(reg);
                }
            }
        }
        return healthy;
    }

    public void recordRequest(String serviceName, boolean success, long latencyMs) {
        ServiceStats stats = statsMap.computeIfAbsent(serviceName, k ->
                ServiceStats.builder().serviceName(k).circuitState(CircuitState.CLOSED).build());
        stats.setTotalRequests(stats.getTotalRequests() + 1);
        if (success) {
            stats.setSuccessCount(stats.getSuccessCount() + 1);
        } else {
            stats.setFailureCount(stats.getFailureCount() + 1);
        }
        // Rolling average latency
        double currentAvg = stats.getAvgLatencyMs();
        long total = stats.getTotalRequests();
        stats.setAvgLatencyMs((currentAvg * (total - 1) + latencyMs) / total);
    }

    public List<ServiceRegistration> getCachedInstances(String serviceName) {
        return serviceCache.getOrDefault(serviceName, Collections.emptyList());
    }
}
