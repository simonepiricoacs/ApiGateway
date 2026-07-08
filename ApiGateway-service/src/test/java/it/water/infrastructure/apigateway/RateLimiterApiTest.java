package it.water.infrastructure.apigateway;

import it.water.core.api.bundle.ApplicationProperties;
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

import java.util.Properties;

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

    @Test
    @Order(11)
    void configuredDefaultRateLimitAppliesWhenNoExplicitRuleMatches() {
        rateLimiterApi.getAllRules().forEach(r -> rateLimiterApi.configureLimit(r.getRuleId(), null));
        Properties props = new Properties();
        props.setProperty("water.apigateway.rate.limiter.default.rpm", "2");
        try {
            applicationProperties.loadProperties(props);
            GatewayRequest request = buildRequest("10.11.11.11");
            int allowed = 0;
            for (int i = 0; i < 4; i++) {
                if (rateLimiterApi.checkRateLimit("10.11.11.11", request).isAllowed()) {
                    allowed++;
                }
            }
            Assertions.assertTrue(allowed <= 2, "Configured default limiter should block after 2 requests, allowed: " + allowed);
        } finally {
            applicationProperties.unloadProperties(props);
        }
    }

    @Test
    @Order(12)
    void tokenBucketRemainingIsZeroWhenBucketExhausted() {
        rateLimiterApi.getAllRules().forEach(r -> rateLimiterApi.configureLimit(r.getRuleId(), null));
        // Capacity=1, so after 1 request the bucket is empty
        RateLimitRule rule = new RateLimitRule("tb-remain-1", RateLimitKeyType.CLIENT_IP, 1, 60, RateLimitAlgorithm.TOKEN_BUCKET);
        rule.setBurstCapacity(1);
        rateLimiterApi.configureLimit("tb-remain-1", rule);

        GatewayRequest request = buildRequest("10.20.20.1");
        // First: allowed, remaining>=0
        RateLimitResult first = rateLimiterApi.checkRateLimit("10.20.20.1", request);
        Assertions.assertTrue(first.isAllowed());
        Assertions.assertTrue(first.getRemaining() >= 0);

        // Second: blocked, remaining=0, resetAfterMs > 0
        RateLimitResult second = rateLimiterApi.checkRateLimit("10.20.20.1", request);
        Assertions.assertFalse(second.isAllowed());
        Assertions.assertEquals(0, second.getRemaining());
        Assertions.assertTrue(second.getResetAfterMs() > 0,
                "resetAfterMs must be positive when bucket is empty");

        rateLimiterApi.configureLimit("tb-remain-1", null);
    }

    @Test
    @Order(13)
    void tokenBucketResetAfterMsIsZeroWhenAllowed() {
        rateLimiterApi.getAllRules().forEach(r -> rateLimiterApi.configureLimit(r.getRuleId(), null));
        RateLimitRule rule = new RateLimitRule("tb-reset-1", RateLimitKeyType.CLIENT_IP, 100, 60, RateLimitAlgorithm.TOKEN_BUCKET);
        rule.setBurstCapacity(100);
        rateLimiterApi.configureLimit("tb-reset-1", rule);

        GatewayRequest request = buildRequest("10.21.21.1");
        RateLimitResult result = rateLimiterApi.checkRateLimit("10.21.21.1", request);
        Assertions.assertTrue(result.isAllowed());
        Assertions.assertEquals(0, result.getResetAfterMs(),
                "resetAfterMs must be 0 when request is allowed");

        rateLimiterApi.configureLimit("tb-reset-1", null);
    }

    @Test
    @Order(14)
    void slidingWindowRemainingAndResetAfterMsWhenBlocked() {
        rateLimiterApi.getAllRules().forEach(r -> rateLimiterApi.configureLimit(r.getRuleId(), null));
        RateLimitRule rule = new RateLimitRule("sw-reset-1", RateLimitKeyType.CLIENT_IP, 1, 60, RateLimitAlgorithm.SLIDING_WINDOW);
        rateLimiterApi.configureLimit("sw-reset-1", rule);

        GatewayRequest request = buildRequest("10.22.22.1");
        // First: allowed
        RateLimitResult first = rateLimiterApi.checkRateLimit("10.22.22.1", request);
        Assertions.assertTrue(first.isAllowed());

        // Second: blocked, remaining=0, resetAfterMs >= 0
        RateLimitResult second = rateLimiterApi.checkRateLimit("10.22.22.1", request);
        Assertions.assertFalse(second.isAllowed());
        Assertions.assertEquals(0, second.getRemaining());
        Assertions.assertTrue(second.getResetAfterMs() >= 0,
                "resetAfterMs must be non-negative when sliding window blocks: " + second.getResetAfterMs());

        rateLimiterApi.configureLimit("sw-reset-1", null);
    }

    @Test
    @Order(15)
    void fixedWindowRemainingAndResetAfterMsWhenBlocked() {
        rateLimiterApi.getAllRules().forEach(r -> rateLimiterApi.configureLimit(r.getRuleId(), null));
        RateLimitRule rule = new RateLimitRule("fw-reset-1", RateLimitKeyType.CLIENT_IP, 1, 60, RateLimitAlgorithm.FIXED_WINDOW);
        rateLimiterApi.configureLimit("fw-reset-1", rule);

        GatewayRequest request = buildRequest("10.23.23.1");
        // First: allowed, remaining=0 (1 - 1 = 0)
        RateLimitResult first = rateLimiterApi.checkRateLimit("10.23.23.1", request);
        Assertions.assertTrue(first.isAllowed());
        Assertions.assertEquals(0, first.getRemaining());

        // Second: blocked
        RateLimitResult second = rateLimiterApi.checkRateLimit("10.23.23.1", request);
        Assertions.assertFalse(second.isAllowed());
        Assertions.assertEquals(0, second.getRemaining());
        Assertions.assertTrue(second.getResetAfterMs() >= 0,
                "resetAfterMs must be non-negative when fixed window blocks: " + second.getResetAfterMs());

        rateLimiterApi.configureLimit("fw-reset-1", null);
    }

    @Test
    @Order(16)
    void configureLimitWithNullRuleRemovesIt() {
        rateLimiterApi.getAllRules().forEach(r -> rateLimiterApi.configureLimit(r.getRuleId(), null));
        RateLimitRule rule = new RateLimitRule("null-remove-1", RateLimitKeyType.CLIENT_IP, 10, 60, RateLimitAlgorithm.TOKEN_BUCKET);
        rateLimiterApi.configureLimit("null-remove-1", rule);
        Assertions.assertNotNull(rateLimiterApi.getRule("null-remove-1"));

        rateLimiterApi.configureLimit("null-remove-1", null);
        Assertions.assertNull(rateLimiterApi.getRule("null-remove-1"),
                "Configuring null should remove the rule");
    }

    @Test
    @Order(17)
    void getAllRulesReturnsEmptyWhenAllRemoved() {
        rateLimiterApi.getAllRules().forEach(r -> rateLimiterApi.configureLimit(r.getRuleId(), null));
        var rules = rateLimiterApi.getAllRules();
        Assertions.assertNotNull(rules);
        Assertions.assertTrue(rules.isEmpty(), "All rules removed → list must be empty");
    }

    @Test
    @Order(18)
    void checkRateLimitWithRulesButNoneMatchingFallsBackToDefault() {
        rateLimiterApi.getAllRules().forEach(r -> rateLimiterApi.configureLimit(r.getRuleId(), null));
        // Add a rule that NEVER matches any key (specific pattern)
        RateLimitRule rule = new RateLimitRule("no-match-rule", RateLimitKeyType.CLIENT_IP, 1, 60, RateLimitAlgorithm.FIXED_WINDOW);
        rule.setKeyPattern("999\\.999\\.999\\.999");
        rateLimiterApi.configureLimit("no-match-rule", rule);

        // No default configured → falls through to allowed:true
        GatewayRequest request = buildRequest("10.30.30.30");
        RateLimitResult result = rateLimiterApi.checkRateLimit("10.30.30.30", request);
        Assertions.assertTrue(result.isAllowed(),
                "When rules exist but none match and no default, request must be allowed");

        rateLimiterApi.configureLimit("no-match-rule", null);
    }

    private GatewayRequest buildRequest(String clientIp) {
        return GatewayRequest.builder()
                .method(HttpMethod.GET)
                .path("/api/test")
                .clientIp(clientIp)
                .build();
    }
}
