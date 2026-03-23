package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.GatewayRequest;
import it.water.infrastructure.apigateway.model.RateLimitResult;
import it.water.infrastructure.apigateway.model.RateLimitRule;
import it.water.core.api.service.Service;

import java.util.List;

/**
 * Rate limiter API - enforces request rate limits.
 */
public interface RateLimiterApi extends Service {
    RateLimitResult checkRateLimit(String key, GatewayRequest request);
    void configureLimit(String ruleId, RateLimitRule rule);
    RateLimitRule getRule(String ruleId);
    List<RateLimitRule> getAllRules();
}
