package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.RateLimitRule;
import it.water.core.api.service.BaseEntitySystemApi;

/**
 * System API for RateLimitRule entity - bypasses permission checks.
 */
public interface RateLimitRuleSystemApi extends BaseEntitySystemApi<RateLimitRule> {
}
