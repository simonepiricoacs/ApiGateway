package it.water.infrastructure.apigateway;

import com.sun.net.httpserver.HttpServer;
import it.water.core.api.bundle.ApplicationProperties;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.infrastructure.apigateway.api.CircuitBreakerApi;
import it.water.infrastructure.apigateway.api.GatewaySystemApi;
import it.water.infrastructure.apigateway.model.CircuitBreakerConfig;
import it.water.infrastructure.apigateway.model.ServiceStats;
import it.water.infrastructure.apigateway.service.GatewaySystemServiceImpl;
import it.water.service.discovery.api.ServiceRegistrationApi;
import it.water.service.discovery.model.ServiceRegistration;
import it.water.service.discovery.model.ServiceStatus;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

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

    @Inject
    @Setter
    private ApplicationProperties applicationProperties;

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
    void syncWithServiceDiscoveryFailsWhenNoDiscoveryIsAvailable() {
        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> gatewaySystemApi.syncWithServiceDiscovery());
        Assertions.assertTrue(error.getMessage().contains("ServiceDiscovery sync failed"));
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
        Assertions.assertThrows(IllegalStateException.class, () -> gatewaySystemApi.syncWithServiceDiscovery());
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
    void syncWithServiceDiscoveryFallsBackToRemoteHttpWhenLocalServiceIsMissing() throws Exception {
        GatewaySystemServiceImpl impl = resolveImpl();
        if (impl == null) {
            return;
        }
        ServiceRegistrationApi originalApi = impl.getServiceRegistrationApi();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Properties props = new Properties();
        AtomicReference<String> requestedPath = new AtomicReference<>();
        httpServer.createContext("/water/internal/serviceregistration/available", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            byte[] body = ("[" +
                    "{\"serviceName\":\"remote-sync-svc\",\"serviceVersion\":\"1.0.0\"," +
                    "\"instanceId\":\"remote-sync-1\",\"endpoint\":\"http://127.0.0.1:19001\"," +
                    "\"protocol\":\"http\",\"status\":\"UP\"}," +
                    "{\"serviceName\":\"remote-sync-svc\",\"serviceVersion\":\"1.0.0\"," +
                    "\"instanceId\":\"remote-sync-2\",\"endpoint\":\"http://127.0.0.1:19002\"," +
                    "\"protocol\":\"http\",\"status\":\"DOWN\"}" +
                    "]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        httpServer.start();
        try {
            impl.setServiceRegistrationApi(null);
            props.setProperty("water.apigateway.service.discovery.url", "http://127.0.0.1:" + httpServer.getAddress().getPort());
            applicationProperties.loadProperties(props);

            gatewaySystemApi.syncWithServiceDiscovery();

            List<ServiceRegistration> healthy = gatewaySystemApi.getHealthyInstances("remote-sync-svc");
            List<ServiceRegistration> cached = impl.getCachedInstances("remote-sync-svc");
            Assertions.assertNotNull(healthy);
            Assertions.assertNotNull(cached);
            Assertions.assertEquals("/water/internal/serviceregistration/available", requestedPath.get());
            Assertions.assertEquals(2, cached.size());
            Assertions.assertEquals(1, healthy.size());
            Assertions.assertEquals("remote-sync-1", healthy.get(0).getInstanceId());
        } finally {
            applicationProperties.unloadProperties(props);
            impl.setServiceRegistrationApi(originalApi);
            gatewaySystemApi.evictServiceFromCache("remote-sync-svc");
            httpServer.stop(0);
        }
    }

    @Test
    @Order(12)
    void syncWithServiceDiscoveryRaisesErrorWhenRemoteSyncFails() throws Exception {
        GatewaySystemServiceImpl impl = resolveImpl();
        if (impl == null) {
            return;
        }
        ServiceRegistrationApi originalApi = impl.getServiceRegistrationApi();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/water/internal/serviceregistration/available", exchange -> {
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        httpServer.start();
        Properties props = new Properties();
        props.setProperty("water.apigateway.service.discovery.url", "http://127.0.0.1:" + httpServer.getAddress().getPort());
        try {
            impl.setServiceRegistrationApi(null);
            applicationProperties.loadProperties(props);

            IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                    () -> gatewaySystemApi.syncWithServiceDiscovery());
            Assertions.assertTrue(error.getMessage().contains("ServiceDiscovery sync failed"));
        } finally {
            applicationProperties.unloadProperties(props);
            impl.setServiceRegistrationApi(originalApi);
            httpServer.stop(0);
        }
    }

    @Test
    @Order(13)
    void refreshServiceCacheReplacesCacheAtomically() throws Exception {
        GatewaySystemServiceImpl impl = resolveImpl();
        if (impl == null) {
            return;
        }
        if (!injectCachedInstances(impl, "old-svc", new ServiceRegistration("old-svc", "1.0", "old-1",
                "http://localhost:18082", "http", ServiceStatus.UP))) {
            return;
        }
        Field refreshMethodFieldProbe = GatewaySystemServiceImpl.class.getDeclaredField("serviceCache");
        refreshMethodFieldProbe.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<ServiceRegistration>> previousCache =
                (Map<String, List<ServiceRegistration>>) refreshMethodFieldProbe.get(impl);

        var refreshMethod = GatewaySystemServiceImpl.class.getDeclaredMethod("refreshServiceCache", List.class);
        refreshMethod.setAccessible(true);
        List<ServiceRegistration> refreshedServices = List.of(
                new ServiceRegistration("new-svc", "1.0", "new-1", "http://localhost:18083", "http", ServiceStatus.UP),
                new ServiceRegistration("new-svc", "1.0", "new-2", "http://localhost:18084", "http", ServiceStatus.DOWN)
        );

        refreshMethod.invoke(impl, refreshedServices);

        @SuppressWarnings("unchecked")
        Map<String, List<ServiceRegistration>> currentCache =
                (Map<String, List<ServiceRegistration>>) refreshMethodFieldProbe.get(impl);
        Assertions.assertNotSame(previousCache, currentCache);
        Assertions.assertFalse(currentCache.containsKey("old-svc"));
        Assertions.assertEquals(2, currentCache.get("new-svc").size());
    }

    @Test
    @Order(14)
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
    @Order(15)
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
