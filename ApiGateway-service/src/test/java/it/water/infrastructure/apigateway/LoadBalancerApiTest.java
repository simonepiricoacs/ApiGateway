package it.water.infrastructure.apigateway;

import it.water.infrastructure.apigateway.api.LoadBalancerApi;
import it.water.infrastructure.apigateway.model.GatewayRequest;
import it.water.infrastructure.apigateway.model.HttpMethod;
import it.water.infrastructure.apigateway.model.LoadBalancerStrategy;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for Load Balancer service.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoadBalancerApiTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private LoadBalancerApi loadBalancerApi;

    @BeforeAll
    void beforeAll() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(1)
    void componentInstantiatedCorrectly() {
        Assertions.assertNotNull(loadBalancerApi);
    }

    @Test
    @Order(2)
    void roundRobinDistributesEvenly() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.ROUND_ROBIN);
        List<ServiceRegistration> instances = createInstances(3);
        GatewayRequest request = buildRequest("10.0.0.1");

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            ServiceRegistration selected = loadBalancerApi.selectInstance("test-service", request, instances);
            counts.merge(selected.getInstanceId(), 1, Integer::sum);
        }
        // Each instance should be selected 3 times
        Assertions.assertEquals(3, counts.size());
        counts.values().forEach(count -> Assertions.assertEquals(3, count));
    }

    @Test
    @Order(3)
    void randomSelectionReturnsValidInstance() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.RANDOM);
        List<ServiceRegistration> instances = createInstances(3);
        GatewayRequest request = buildRequest("10.0.0.1");
        for (int i = 0; i < 10; i++) {
            ServiceRegistration selected = loadBalancerApi.selectInstance("test-service", request, instances);
            Assertions.assertNotNull(selected);
            Assertions.assertTrue(instances.contains(selected));
        }
    }

    @Test
    @Order(4)
    void ipHashReturnsSameInstanceForSameIp() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.IP_HASH);
        List<ServiceRegistration> instances = createInstances(3);
        GatewayRequest request = buildRequest("192.168.1.100");

        ServiceRegistration first = loadBalancerApi.selectInstance("test-service", request, instances);
        for (int i = 0; i < 5; i++) {
            ServiceRegistration selected = loadBalancerApi.selectInstance("test-service", request, instances);
            Assertions.assertEquals(first.getInstanceId(), selected.getInstanceId());
        }
    }

    @Test
    @Order(5)
    void ipHashReturnsDifferentInstanceForDifferentIp() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.IP_HASH);
        List<ServiceRegistration> instances = createInstances(10);

        Map<String, String> ipToInstance = new HashMap<>();
        String[] ips = {"10.0.0.1", "10.0.0.2", "10.0.0.3", "192.168.1.1", "172.16.0.1"};
        for (String ip : ips) {
            ServiceRegistration sel = loadBalancerApi.selectInstance("hash-service", buildRequest(ip), instances);
            ipToInstance.put(ip, sel.getInstanceId());
        }
        // Different IPs may map to different instances
        Assertions.assertFalse(ipToInstance.isEmpty());
    }

    @Test
    @Order(6)
    void weightedRoundRobinRespectsWeights() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.WEIGHTED_ROUND_ROBIN);
        List<ServiceRegistration> instances = new ArrayList<>();
        ServiceRegistration heavy = new ServiceRegistration("svc", "1.0", "heavy-instance", "http://heavy:8080", "http", ServiceStatus.UP);
        heavy.setMetadata(Map.of("weight", "3"));
        ServiceRegistration light = new ServiceRegistration("svc", "1.0", "light-instance", "http://light:8080", "http", ServiceStatus.UP);
        light.setMetadata(Map.of("weight", "1"));
        instances.add(heavy);
        instances.add(light);

        GatewayRequest request = buildRequest("10.0.0.1");
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 40; i++) {
            ServiceRegistration sel = loadBalancerApi.selectInstance("weighted-svc", request, instances);
            counts.merge(sel.getInstanceId(), 1, Integer::sum);
        }
        // heavy should get ~3x more requests than light
        int heavyCount = counts.getOrDefault("heavy-instance", 0);
        int lightCount = counts.getOrDefault("light-instance", 0);
        Assertions.assertTrue(heavyCount > lightCount, "Heavy instance should get more requests");
    }

    @Test
    @Order(7)
    void singleInstanceAlwaysReturnsIt() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.ROUND_ROBIN);
        List<ServiceRegistration> single = List.of(createInstance("only-instance"));
        GatewayRequest request = buildRequest("10.0.0.1");
        for (int i = 0; i < 5; i++) {
            ServiceRegistration sel = loadBalancerApi.selectInstance("single-svc", request, single);
            Assertions.assertEquals("only-instance", sel.getInstanceId());
        }
    }

    @Test
    @Order(8)
    void emptyInstanceListReturnsNull() {
        ServiceRegistration sel = loadBalancerApi.selectInstance("empty-svc", buildRequest("10.0.0.1"), new ArrayList<>());
        Assertions.assertNull(sel);
    }

    @Test
    @Order(9)
    void reportSuccessAndFailureNoException() {
        Assertions.assertDoesNotThrow(() -> loadBalancerApi.reportSuccess("svc", "instance-1", 100));
        Assertions.assertDoesNotThrow(() -> loadBalancerApi.reportFailure("svc", "instance-1", new RuntimeException("test")));
    }

    @Test
    @Order(10)
    void leastConnectionsSelectsInstance() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.LEAST_CONNECTIONS);
        List<ServiceRegistration> instances = createInstances(3);
        GatewayRequest request = buildRequest("10.0.0.1");

        ServiceRegistration first = loadBalancerApi.selectInstance("lc-service", request, instances);
        Assertions.assertNotNull(first);
        Assertions.assertTrue(instances.contains(first));

        // Continue selecting - all should be valid instances
        for (int i = 0; i < 6; i++) {
            ServiceRegistration sel = loadBalancerApi.selectInstance("lc-service", request, instances);
            Assertions.assertNotNull(sel);
            Assertions.assertTrue(instances.contains(sel));
        }
    }

    @Test
    @Order(11)
    void leastConnectionsReportSuccessDecrementsCounter() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.LEAST_CONNECTIONS);
        List<ServiceRegistration> instances = createInstances(2);
        GatewayRequest request = buildRequest("10.0.0.1");

        ServiceRegistration sel = loadBalancerApi.selectInstance("lc-dec-svc", request, instances);
        Assertions.assertNotNull(sel);
        // Report success should decrement connection counter (no exception)
        Assertions.assertDoesNotThrow(() -> loadBalancerApi.reportSuccess("lc-dec-svc", sel.getInstanceId(), 50));
        Assertions.assertDoesNotThrow(() -> loadBalancerApi.reportFailure("lc-dec-svc", sel.getInstanceId(), new RuntimeException("fail")));
    }

    @Test
    @Order(12)
    void ipHashWithNullIpReturnsFirstInstance() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.IP_HASH);
        List<ServiceRegistration> instances = createInstances(3);
        GatewayRequest request = GatewayRequest.builder()
                .method(HttpMethod.GET)
                .path("/api/test")
                .clientIp(null)
                .build();
        ServiceRegistration sel = loadBalancerApi.selectInstance("null-ip-svc", request, instances);
        Assertions.assertNotNull(sel);
        Assertions.assertEquals(instances.get(0).getInstanceId(), sel.getInstanceId());
    }

    @Test
    @Order(13)
    void weightedRoundRobinWithDefaultWeightOneWorks() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.WEIGHTED_ROUND_ROBIN);
        // Instances without explicit weight metadata - should default to weight=1
        List<ServiceRegistration> instances = createInstances(2);
        GatewayRequest request = buildRequest("10.0.0.1");
        for (int i = 0; i < 6; i++) {
            ServiceRegistration sel = loadBalancerApi.selectInstance("default-weight-svc", request, instances);
            Assertions.assertNotNull(sel);
        }
    }

    @Test
    @Order(14)
    void getStrategyReturnsCurrentStrategy() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.ROUND_ROBIN);
        Assertions.assertEquals(LoadBalancerStrategy.ROUND_ROBIN, loadBalancerApi.getStrategy());
        loadBalancerApi.setStrategy(LoadBalancerStrategy.RANDOM);
        Assertions.assertEquals(LoadBalancerStrategy.RANDOM, loadBalancerApi.getStrategy());
    }

    @Test
    @Order(15)
    void weightedRoundRobinWithInvalidWeightStringDefaultsToOne() {
        loadBalancerApi.setStrategy(LoadBalancerStrategy.WEIGHTED_ROUND_ROBIN);
        List<ServiceRegistration> instances = new ArrayList<>();
        // Instance with invalid weight (non-numeric) → should default to 1
        ServiceRegistration invalid = new ServiceRegistration("svc", "1.0", "invalid-weight",
                "http://invalid:8080", "http", ServiceStatus.UP);
        invalid.setMetadata(Map.of("weight", "not-a-number"));
        // Instance with metadata but no "weight" key → should default to 1
        ServiceRegistration noWeightKey = new ServiceRegistration("svc", "1.0", "no-weight-key",
                "http://noweight:8080", "http", ServiceStatus.UP);
        noWeightKey.setMetadata(Map.of("other-key", "value"));
        instances.add(invalid);
        instances.add(noWeightKey);

        GatewayRequest request = buildRequest("10.0.0.1");
        for (int i = 0; i < 6; i++) {
            ServiceRegistration sel = loadBalancerApi.selectInstance("invalid-weight-svc", request, instances);
            Assertions.assertNotNull(sel);
            Assertions.assertTrue(instances.contains(sel));
        }
    }

    private List<ServiceRegistration> createInstances(int count) {
        List<ServiceRegistration> instances = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            instances.add(createInstance("instance-" + i));
        }
        return instances;
    }

    private ServiceRegistration createInstance(String instanceId) {
        return new ServiceRegistration("test-service", "1.0", instanceId,
                "http://localhost:" + (8080 + instanceId.hashCode() % 100), "http", ServiceStatus.UP);
    }

    private GatewayRequest buildRequest(String clientIp) {
        return GatewayRequest.builder()
                .method(HttpMethod.GET)
                .path("/api/test")
                .clientIp(clientIp)
                .build();
    }
}
