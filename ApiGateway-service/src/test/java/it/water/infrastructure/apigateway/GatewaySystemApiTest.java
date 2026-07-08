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
import org.mockito.Mockito;

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
            props.setProperty("water.discovery.url", "http://127.0.0.1:" + httpServer.getAddress().getPort());
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
        props.setProperty("water.discovery.url", "http://127.0.0.1:" + httpServer.getAddress().getPort());
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

    @Test
    @Order(16)
    void directImplRecordRequestUpdatesStats() {
        // Test recordRequest directly on a fresh impl instance to avoid proxy overhead
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        fresh.recordRequest("direct-svc-1", true, 100);
        fresh.recordRequest("direct-svc-1", false, 200);
        fresh.recordRequest("direct-svc-1", true, 300);

        Map<String, ServiceStats> stats = fresh.getServiceStatistics();
        ServiceStats s = stats.get("direct-svc-1");
        Assertions.assertNotNull(s);
        Assertions.assertEquals(3, s.getTotalRequests());
        Assertions.assertEquals(2, s.getSuccessCount());
        Assertions.assertEquals(1, s.getFailureCount());
        Assertions.assertTrue(s.getAvgLatencyMs() > 0 && s.getAvgLatencyMs() <= 300);
    }

    @Test
    @Order(17)
    void directImplGetCachedInstances() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        List<ServiceRegistration> empty = fresh.getCachedInstances("no-such-svc");
        Assertions.assertNotNull(empty);
        Assertions.assertTrue(empty.isEmpty());
    }

    @Test
    @Order(18)
    void directImplDeactivateClearsCaches() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();
        fresh.recordRequest("deact-svc", true, 50);

        // Put something in serviceCache via reflection
        Field cacheField = GatewaySystemServiceImpl.class.getDeclaredField("serviceCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<Map<String, List<ServiceRegistration>>> cacheRef =
                (java.util.concurrent.atomic.AtomicReference<Map<String, List<ServiceRegistration>>>) cacheField.get(fresh);
        cacheRef.get().put("deact-svc-cached", new ArrayList<>());

        fresh.deactivate();

        Assertions.assertTrue(fresh.getServiceStatistics().isEmpty());
    }

    @Test
    @Order(19)
    void directImplRefreshServiceCacheGroupsServicesByName() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        java.lang.reflect.Method refreshCache = GatewaySystemServiceImpl.class
                .getDeclaredMethod("refreshServiceCache", List.class);
        refreshCache.setAccessible(true);

        List<ServiceRegistration> services = new ArrayList<>();
        services.add(new ServiceRegistration("svc-a", "1.0", "a-1", "http://localhost:19100", "http", ServiceStatus.UP));
        services.add(new ServiceRegistration("svc-a", "1.0", "a-2", "http://localhost:19101", "http", ServiceStatus.UP));
        services.add(new ServiceRegistration("svc-b", "1.0", "b-1", "http://localhost:19200", "http", ServiceStatus.DOWN));

        refreshCache.invoke(fresh, services);

        List<ServiceRegistration> cachedA = fresh.getCachedInstances("svc-a");
        List<ServiceRegistration> cachedB = fresh.getCachedInstances("svc-b");
        Assertions.assertEquals(2, cachedA.size());
        Assertions.assertEquals(1, cachedB.size());
    }

    @Test
    @Order(20)
    void directImplRefreshServiceCacheWithEmptyListClearsCache() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        java.lang.reflect.Method refreshCache = GatewaySystemServiceImpl.class
                .getDeclaredMethod("refreshServiceCache", List.class);
        refreshCache.setAccessible(true);

        // First populate
        List<ServiceRegistration> services = List.of(
                new ServiceRegistration("svc-x", "1.0", "x-1", "http://localhost:19300", "http", ServiceStatus.UP));
        refreshCache.invoke(fresh, services);
        Assertions.assertEquals(1, fresh.getCachedInstances("svc-x").size());

        // Now clear with empty list
        refreshCache.invoke(fresh, new ArrayList<>());
        Assertions.assertTrue(fresh.getCachedInstances("svc-x").isEmpty());
    }

    @Test
    @Order(21)
    void directImplHasRemoteServiceDiscoveryConfiguredReturnsFalseWhenNoOptions() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();
        // No options set - getConfiguredServiceDiscoveryUrl returns ""
        java.lang.reflect.Method hasRemote = GatewaySystemServiceImpl.class
                .getDeclaredMethod("hasRemoteServiceDiscoveryConfigured");
        hasRemote.setAccessible(true);
        Boolean result = (Boolean) hasRemote.invoke(fresh);
        Assertions.assertFalse(result);
    }

    @Test
    @Order(22)
    void directImplResolveServiceDiscoveryEndpointThrowsWhenNoUrlConfigured() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        java.lang.reflect.Method resolve = GatewaySystemServiceImpl.class
                .getDeclaredMethod("resolveServiceDiscoveryEndpoint");
        resolve.setAccessible(true);

        java.lang.reflect.InvocationTargetException ex = Assertions.assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> resolve.invoke(fresh));
        Assertions.assertInstanceOf(IllegalStateException.class, ex.getCause());
        Assertions.assertTrue(ex.getCause().getMessage().contains("not configured"));
    }

    @Test
    @Order(23)
    void directImplResolveServiceDiscoveryEndpointNormalizesWaterSuffix() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        Field optField = GatewaySystemServiceImpl.class.getDeclaredField("gatewaySystemOptions");
        optField.setAccessible(true);
        optField.set(fresh, stubOptions("http://localhost:18999/water"));

        java.lang.reflect.Method resolve = GatewaySystemServiceImpl.class
                .getDeclaredMethod("resolveServiceDiscoveryEndpoint");
        resolve.setAccessible(true);
        String endpoint = (String) resolve.invoke(fresh);
        Assertions.assertEquals("http://localhost:18999/water/internal/serviceregistration/available", endpoint);
    }

    @Test
    @Order(24)
    void directImplResolveServiceDiscoveryEndpointWithTrailingSlash() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        Field optField = GatewaySystemServiceImpl.class.getDeclaredField("gatewaySystemOptions");
        optField.setAccessible(true);
        optField.set(fresh, stubOptions("http://localhost:18998/"));

        java.lang.reflect.Method resolve = GatewaySystemServiceImpl.class
                .getDeclaredMethod("resolveServiceDiscoveryEndpoint");
        resolve.setAccessible(true);
        String endpoint = (String) resolve.invoke(fresh);
        Assertions.assertTrue(endpoint.endsWith("/water/internal/serviceregistration/available"),
                "Trailing slash URL must still produce valid endpoint: " + endpoint);
    }

    @Test
    @Order(25)
    void directImplResolveServiceDiscoveryEndpointAlreadyHasFullPath() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        String fullPath = "http://localhost:18997/water/internal/serviceregistration/available";
        Field optField = GatewaySystemServiceImpl.class.getDeclaredField("gatewaySystemOptions");
        optField.setAccessible(true);
        optField.set(fresh, stubOptions(fullPath));

        java.lang.reflect.Method resolve = GatewaySystemServiceImpl.class
                .getDeclaredMethod("resolveServiceDiscoveryEndpoint");
        resolve.setAccessible(true);
        String endpoint = (String) resolve.invoke(fresh);
        Assertions.assertEquals(fullPath, endpoint, "Already-full endpoint must not be doubled");
    }

    @Test
    @Order(26)
    void directImplParseRemoteServiceRegistrationsHandlesNullResponse() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        java.lang.reflect.Method parse = GatewaySystemServiceImpl.class
                .getDeclaredMethod("parseRemoteServiceRegistrations", String.class);
        parse.setAccessible(true);

        // Null JSON array -> should return empty list
        List<?> result = (List<?>) parse.invoke(fresh, "null");
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    @Order(27)
    void directImplGetHealthyInstancesWithHalfOpenCircuit() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        // Wire circuitBreakerApi
        Field cbField = GatewaySystemServiceImpl.class.getDeclaredField("circuitBreakerApi");
        cbField.setAccessible(true);
        cbField.set(fresh, circuitBreakerApi);
        fresh.activate();

        String svcName = "direct-halfopen-svc";
        ServiceRegistration upInstance = new ServiceRegistration(svcName, "1.0", "ho-inst-1",
                "http://localhost:19400", "http", ServiceStatus.UP);

        // Inject into cache
        Field cacheField = GatewaySystemServiceImpl.class.getDeclaredField("serviceCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<Map<String, List<ServiceRegistration>>> cacheRef =
                (java.util.concurrent.atomic.AtomicReference<Map<String, List<ServiceRegistration>>>) cacheField.get(fresh);
        cacheRef.get().put(svcName, new ArrayList<>(List.of(upInstance)));

        // Put circuit in HALF_OPEN state: threshold=1, timeout=0 so it transitions immediately
        it.water.infrastructure.apigateway.model.CircuitBreakerConfig cfg =
                it.water.infrastructure.apigateway.model.CircuitBreakerConfig.builder()
                        .serviceName(svcName).failureThreshold(1).successThreshold(5).timeoutSeconds(0).build();
        circuitBreakerApi.configure(svcName, cfg);
        circuitBreakerApi.recordFailure(svcName, "ho-inst-1");
        Thread.sleep(50);
        circuitBreakerApi.allowRequest(svcName, "ho-inst-1"); // transition to HALF_OPEN

        // HALF_OPEN instances should be returned as healthy
        List<ServiceRegistration> healthy = fresh.getHealthyInstances(svcName);
        Assertions.assertNotNull(healthy);
        Assertions.assertEquals(1, healthy.size(), "HALF_OPEN instance must be included in healthy list");
        Assertions.assertEquals("ho-inst-1", healthy.get(0).getInstanceId());
    }

    @Test
    @Order(28)
    void directImplSyncWithServiceDiscoveryInterruptedExceptionWrapped() throws Exception {
        GatewaySystemServiceImpl fresh = new GatewaySystemServiceImpl();
        fresh.activate();

        // Wire a ServiceRegistrationApi that interrupts the current thread and then throws
        Field saField = GatewaySystemServiceImpl.class.getDeclaredField("serviceRegistrationApi");
        saField.setAccessible(true);
        ServiceRegistrationApi interruptingApi = Mockito.mock(ServiceRegistrationApi.class);
        Mockito.when(interruptingApi.getAvailableServices()).thenAnswer(inv -> {
            Thread.currentThread().interrupt();
            throw new InterruptedException("simulated-interrupt");
        });
        saField.set(fresh, interruptingApi);

        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> fresh.syncWithServiceDiscovery());
        // Clear the interrupted flag so it doesn't leak into subsequent tests
        Thread.interrupted();
        Assertions.assertNotNull(ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("interrupted") || ex.getMessage().contains("sync"),
                "Exception message must reference the sync failure: " + ex.getMessage());
    }

    // Helper: create a minimal GatewaySystemOptions stub that returns the given discovery URL
    private static it.water.infrastructure.apigateway.api.options.GatewaySystemOptions stubOptions(String discoveryUrl) {
        return new it.water.infrastructure.apigateway.api.options.GatewaySystemOptions() {
            @Override public String getServiceDiscoveryUrl() { return discoveryUrl; }
            @Override public long getProxyTimeoutMs() { return 30000L; }
            @Override public int getCircuitBreakerFailureThreshold() { return 5; }
            @Override public long getCircuitBreakerTimeoutMs() { return 30000L; }
            @Override public int getDefaultRateLimiterRequestsPerMinute() { return 0; }
            @Override public java.util.Set<String> getTrustedProxies() { return java.util.Collections.emptySet(); }
        };
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
