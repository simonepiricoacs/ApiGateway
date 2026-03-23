package it.water.infrastructure.apigateway;

import it.water.infrastructure.apigateway.api.RateLimiterApi;
import it.water.infrastructure.apigateway.model.*;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Unit tests for Rate Limiter service.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimiterApiTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private RateLimiterApi rateLimiterApi;

    @BeforeAll
    void beforeAll() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(1)
    void componentInstantiatedCorrectly() {
        Assertions.assertNotNull(rateLimiterApi);
    }

    @Test
    @Order(2)
    void noRulesAllowsAllRequests() {
        GatewayRequest request = buildRequest("10.0.0.1");
        RateLimitResult result = rateLimiterApi.checkRateLimit("10.0.0.1", request);
        Assertions.assertTrue(result.isAllowed());
    }

    @Test
    @Order(3)
    void tokenBucketAllowsUpToMaxRequests() {
        RateLimitRule rule = new RateLimitRule("tb-rule-1", RateLimitKeyType.CLIENT_IP, 5, 60, RateLimitAlgorithm.TOKEN_BUCKET);
        rule.setBurstCapacity(5);
        rateLimiterApi.configureLimit("tb-rule-1", rule);

        GatewayRequest request = buildRequest("192.168.1.1");
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            RateLimitResult result = rateLimiterApi.checkRateLimit("192.168.1.1", request);
            if (result.isAllowed()) allowed++;
        }
        Assertions.assertTrue(allowed >= 5 && allowed <= 6, "Should allow ~5 requests, got: " + allowed);
    }

    @Test
    @Order(4)
    void fixedWindowBlocksAfterLimit() throws InterruptedException {
        RateLimitRule rule = new RateLimitRule("fw-rule-1", RateLimitKeyType.CLIENT_IP, 3, 1, RateLimitAlgorithm.FIXED_WINDOW);
        rateLimiterApi.configureLimit("fw-rule-1", rule);

        // Remove token bucket rule to test fixed window
        rateLimiterApi.configureLimit("tb-rule-1", null);

        GatewayRequest request = buildRequest("10.1.1.1");
        int allowed = 0;
        for (int i = 0; i < 6; i++) {
            RateLimitResult result = rateLimiterApi.checkRateLimit("10.1.1.1", request);
            if (result.isAllowed()) allowed++;
        }
        Assertions.assertTrue(allowed <= 3, "Should block after 3 requests within window, allowed: " + allowed);
    }

    @Test
    @Order(5)
    void slidingWindowBlocksAfterLimit() {
        RateLimitRule rule = new RateLimitRule("sw-rule-1", RateLimitKeyType.CLIENT_IP, 3, 60, RateLimitAlgorithm.SLIDING_WINDOW);
        rateLimiterApi.configureLimit("sw-rule-1", rule);

        // Remove fixed window rule
        rateLimiterApi.configureLimit("fw-rule-1", null);

        GatewayRequest request = buildRequest("10.2.2.2");
        int allowed = 0;
        for (int i = 0; i < 6; i++) {
            RateLimitResult result = rateLimiterApi.checkRateLimit("10.2.2.2", request);
            if (result.isAllowed()) allowed++;
        }
        Assertions.assertTrue(allowed <= 3, "Sliding window should block after 3 requests, allowed: " + allowed);
    }

    @Test
    @Order(6)
    void getRuleReturnsConfiguredRule() {
        RateLimitRule rule = new RateLimitRule("get-rule", RateLimitKeyType.GLOBAL, 100, 60, RateLimitAlgorithm.TOKEN_BUCKET);
        rateLimiterApi.configureLimit("get-rule", rule);
        RateLimitRule retrieved = rateLimiterApi.getRule("get-rule");
        Assertions.assertNotNull(retrieved);
        Assertions.assertEquals("get-rule", retrieved.getRuleId());
        Assertions.assertEquals(100, retrieved.getMaxRequests());
    }

    @Test
    @Order(7)
    void getAllRulesReturnsConfiguredRules() {
        var rules = rateLimiterApi.getAllRules();
        Assertions.assertNotNull(rules);
        Assertions.assertFalse(rules.isEmpty());
    }

    @Test
    @Order(8)
    void rateLimitResultHasCorrectFields() {
        RateLimitRule rule = new RateLimitRule("result-rule", RateLimitKeyType.CLIENT_IP, 10, 60, RateLimitAlgorithm.TOKEN_BUCKET);
        rule.setBurstCapacity(10);
        rateLimiterApi.configureLimit("result-rule", rule);

        // Remove other rules
        rateLimiterApi.configureLimit("sw-rule-1", null);
        rateLimiterApi.configureLimit("get-rule", null);

        GatewayRequest request = buildRequest("10.3.3.3");
        RateLimitResult result = rateLimiterApi.checkRateLimit("10.3.3.3", request);
        Assertions.assertTrue(result.isAllowed());
        Assertions.assertTrue(result.getRemaining() >= 0);
        Assertions.assertEquals(0, result.getResetAfterMs());
    }

    @Test
    @Order(9)
    void disabledRuleIsSkippedAndAllowsRequests() {
        // Remove active rules, leave only a disabled one
        rateLimiterApi.configureLimit("result-rule", null);
        RateLimitRule disabled = new RateLimitRule("disabled-rl-1", RateLimitKeyType.CLIENT_IP, 1, 60, RateLimitAlgorithm.TOKEN_BUCKET);
        disabled.setEnabled(false);
        rateLimiterApi.configureLimit("disabled-rl-1", disabled);

        GatewayRequest request = buildRequest("10.5.5.5");
        // maxRequests=1 but rule is disabled; all requests must pass
        for (int i = 0; i < 5; i++) {
            RateLimitResult result = rateLimiterApi.checkRateLimit("10.5.5.5", request);
            Assertions.assertTrue(result.isAllowed(), "Disabled rule must not block request " + i);
        }
        // Clean up
        rateLimiterApi.configureLimit("disabled-rl-1", null);
    }

    @Test
    @Order(10)
    void keyPatternMatchingAppliesRuleOnlyToMatchingKeys() {
        // Rule applies only to keys matching "10\\.10\\..*"
        RateLimitRule rule = new RateLimitRule("pattern-rl-1", RateLimitKeyType.CLIENT_IP, 2, 60, RateLimitAlgorithm.FIXED_WINDOW);
        rule.setKeyPattern("10\\.10\\..*");
        rateLimiterApi.configureLimit("pattern-rl-1", rule);

        // Key matching the pattern: blocked after 2 requests
        GatewayRequest req = buildRequest("10.10.0.1");
        int allowed = 0;
        for (int i = 0; i < 5; i++) {
            if (rateLimiterApi.checkRateLimit("10.10.0.1", req).isAllowed()) allowed++;
        }
        Assertions.assertTrue(allowed <= 2, "Pattern-matched key should be rate-limited after 2, got: " + allowed);

        // Key NOT matching the pattern: always allowed
        RateLimitResult nonMatching = rateLimiterApi.checkRateLimit("192.168.1.1", buildRequest("192.168.1.1"));
        Assertions.assertTrue(nonMatching.isAllowed(), "Non-matching key must not be rate-limited");

        // Clean up
        rateLimiterApi.configureLimit("pattern-rl-1", null);
    }

    private GatewayRequest buildRequest(String clientIp) {
        return GatewayRequest.builder()
                .method(HttpMethod.GET)
                .path("/api/test")
                .clientIp(clientIp)
                .build();
    }
}
