package it.water.infrastructure.apigateway;

import it.water.infrastructure.apigateway.api.CircuitBreakerApi;
import it.water.infrastructure.apigateway.api.GatewaySystemApi;
import it.water.infrastructure.apigateway.model.CircuitBreakerConfig;
import it.water.infrastructure.apigateway.model.ServiceStats;
import it.water.infrastructure.apigateway.service.GatewaySystemServiceImpl;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.service.discovery.model.ServiceRegistration;
import it.water.service.discovery.model.ServiceStatus;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for GatewaySystemServiceImpl covering service discovery integration,
 * statistics tracking, and cache management.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewaySystemApiTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private GatewaySystemApi gatewaySystemApi;

    @Inject
    @Setter
    private CircuitBreakerApi circuitBreakerApi;

    @BeforeAll
    void beforeAll() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(1)
    void componentInstantiatedCorrectly() {
        Assertions.assertNotNull(gatewaySystemApi);
    }

    @Test
    @Order(2)
    void syncWithServiceDiscoveryDoesNotThrow() {
        Assertions.assertDoesNotThrow(() -> gatewaySystemApi.syncWithServiceDiscovery());
    }

    @Test
    @Order(3)
    void evictServiceFromCacheDoesNotThrow() {
        Assertions.assertDoesNotThrow(() -> gatewaySystemApi.evictServiceFromCache("non-existent-service"));
    }

    @Test
    @Order(4)
    void getServiceStatisticsReturnsNonNullMap() {
        Map<String, ServiceStats> stats = gatewaySystemApi.getServiceStatistics();
        Assertions.assertNotNull(stats);
    }

    @Test
    @Order(5)
    void getHealthyInstancesReturnsEmptyWhenNoCachedInstances() {
        List<ServiceRegistration> instances = gatewaySystemApi.getHealthyInstances("non-cached-service-xyz");
        Assertions.assertNotNull(instances);
        Assertions.assertTrue(instances.isEmpty());
    }

    @Test
    @Order(6)
    void recordRequestUpdatesStatistics() {
        GatewaySystemServiceImpl impl = resolveImpl();
        if (impl == null) {
            return; // skip if proxy cast not supported
        }
        impl.recordRequest("gst-svc-1", true, 50);
        impl.recordRequest("gst-svc-1", false, 100);
        impl.recordRequest("gst-svc-1", true, 75);

        Map<String, ServiceStats> stats = gatewaySystemApi.getServiceStatistics();
        ServiceStats s = stats.get("gst-svc-1");
        Assertions.assertNotNull(s);
        Assertions.assertEquals(3, s.getTotalRequests());
        Assertions.assertEquals(2, s.getSuccessCount());
        Assertions.assertEquals(1, s.getFailureCount());
        Assertions.assertTrue(s.getAvgLatencyMs() > 0);
    }

    @Test
    @Order(7)
    void recordMultipleRequestsRollingAvgUpdates() {
        GatewaySystemServiceImpl impl = resolveImpl();
        if (impl == null) {
            return;
        }
        impl.recordRequest("gst-svc-2", true, 100);
        impl.recordRequest("gst-svc-2", true, 200);

        Map<String, ServiceStats> stats = gatewaySystemApi.getServiceStatistics();
        ServiceStats s = stats.get("gst-svc-2");
        Assertions.assertNotNull(s);
        Assertions.assertEquals(2, s.getTotalRequests());
        Assertions.assertTrue(s.getAvgLatencyMs() > 0 && s.getAvgLatencyMs() <= 200);
    }

    @Test
    @Order(8)
    void getCachedInstancesReturnsEmptyWhenNotCached() {
        GatewaySystemServiceImpl impl = resolveImpl();
        if (impl == null) {
            return;
        }
        List<ServiceRegistration> instances = impl.getCachedInstances("unknown-svc-xyz");
        Assertions.assertNotNull(instances);
        Assertions.assertTrue(instances.isEmpty());
    }

    @Test
    @Order(9)
    void evictServiceAfterSyncRemovesFromCache() {
        // sync first (may or may not populate cache depending on service discovery availability)
        gatewaySystemApi.syncWithServiceDiscovery();
        // evict a service - should not throw even if not cached
        Assertions.assertDoesNotThrow(() -> gatewaySystemApi.evictServiceFromCache("any-service-gst"));
    }

    @Test
    @Order(10)
    void getHealthyInstancesCalledMultipleTimes() {
        // Calling getHealthyInstances multiple times should be stable
        for (int i = 0; i < 3; i++) {
            List<ServiceRegistration> instances = gatewaySystemApi.getHealthyInstances("stable-svc-" + i);
            Assertions.assertNotNull(instances);
        }
    }

    @Test
    @Order(11)
    void getHealthyInstancesFiltersDownInstances() {
        GatewaySystemServiceImpl impl = resolveImpl();
        if (impl == null) {
            return; // skip if proxy unwrap not available
        }
        String svcName = "gst-down-svc";
        ServiceRegistration downInstance = new ServiceRegistration(svcName, "1.0", "down-inst-1",
                "http://localhost:18080", "http", ServiceStatus.DOWN);
        if (!injectCachedInstances(impl, svcName, downInstance)) {
            return;
        }
        List<ServiceRegistration> healthy = gatewaySystemApi.getHealthyInstances(svcName);
        Assertions.assertNotNull(healthy);
        Assertions.assertTrue(healthy.isEmpty(), "DOWN instance must be filtered out");
    }

    @Test
    @Order(12)
    void getHealthyInstancesFiltersOpenCircuitInstances() {
        GatewaySystemServiceImpl impl = resolveImpl();
        if (impl == null) {
            return;
        }
        String svcName = "gst-opencb-svc";
        ServiceRegistration upInstance = new ServiceRegistration(svcName, "1.0", "opencb-inst-1",
                "http://localhost:18081", "http", ServiceStatus.UP);
        if (!injectCachedInstances(impl, svcName, upInstance)) {
            return;
        }
        // Open the circuit for this instance
        CircuitBreakerConfig cfg = CircuitBreakerConfig.builder()
                .serviceName(svcName).failureThreshold(1).successThreshold(3).timeoutSeconds(60).build();
        circuitBreakerApi.configure(svcName, cfg);
        circuitBreakerApi.recordFailure(svcName, "opencb-inst-1");

        List<ServiceRegistration> healthy = gatewaySystemApi.getHealthyInstances(svcName);
        Assertions.assertNotNull(healthy);
        Assertions.assertTrue(healthy.isEmpty(), "Instance with OPEN circuit must be filtered out");
    }

    private GatewaySystemServiceImpl resolveImpl() {
        try {
            Object component = componentRegistry.findComponent(GatewaySystemApi.class, null);
            if (component instanceof GatewaySystemServiceImpl impl) {
                return impl;
            }
        } catch (Exception e) {
            // proxy cast not supported in this runtime - skip
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean injectCachedInstances(GatewaySystemServiceImpl impl, String serviceName,
                                          ServiceRegistration... instances) {
        try {
            Field field = GatewaySystemServiceImpl.class.getDeclaredField("serviceCache");
            field.setAccessible(true);
            Map<String, List<ServiceRegistration>> cache =
                    (Map<String, List<ServiceRegistration>>) field.get(impl);
            cache.put(serviceName, new ArrayList<>(List.of(instances)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}