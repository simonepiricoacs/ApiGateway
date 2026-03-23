package it.water.infrastructure.apigateway.service.rest;

import it.water.infrastructure.apigateway.api.RateLimitRuleApi;
import it.water.infrastructure.apigateway.api.rest.RateLimitRuleRestApi;
import it.water.infrastructure.apigateway.model.RateLimitRule;
import it.water.core.api.service.BaseEntityApi;
import it.water.core.api.service.rest.FrameworkRestController;
import it.water.core.interceptors.annotations.Inject;
import it.water.service.rest.persistence.BaseEntityRestApi;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller implementation for RateLimitRule entity.
 */
@FrameworkRestController(referredRestApi = RateLimitRuleRestApi.class)
public class RateLimitRuleRestControllerImpl extends BaseEntityRestApi<RateLimitRule> implements RateLimitRuleRestApi {

    @SuppressWarnings("java:S1068")
    private static final Logger log = LoggerFactory.getLogger(RateLimitRuleRestControllerImpl.class);

    @Inject
    @Setter
    private RateLimitRuleApi rateLimitRuleApi;

    @Override
    protected BaseEntityApi<RateLimitRule> getEntityService() {
        return rateLimitRuleApi;
    }
}
