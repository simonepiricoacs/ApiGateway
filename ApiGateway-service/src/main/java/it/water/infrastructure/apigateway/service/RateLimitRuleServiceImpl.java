package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.RateLimitRuleApi;
import it.water.infrastructure.apigateway.api.RateLimitRuleRepository;
import it.water.infrastructure.apigateway.api.RateLimitRuleSystemApi;
import it.water.infrastructure.apigateway.model.RateLimitRule;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.repository.service.BaseEntityServiceImpl;
import lombok.Getter;
import lombok.Setter;

/**
 * Service implementation for RateLimitRule entity.
 */
@FrameworkComponent
public class RateLimitRuleServiceImpl extends BaseEntityServiceImpl<RateLimitRule> implements RateLimitRuleApi {

    @Inject
    @Getter
    @Setter
    private RateLimitRuleSystemApi systemService;

    @Inject
    @Getter
    @Setter
    private RateLimitRuleRepository repository;

    @Inject
    @Getter
    @Setter
    private ComponentRegistry componentRegistry;

    public RateLimitRuleServiceImpl() {
        super(RateLimitRule.class);
    }
}
