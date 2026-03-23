package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.RateLimitRuleRepository;
import it.water.infrastructure.apigateway.api.RateLimitRuleSystemApi;
import it.water.infrastructure.apigateway.model.RateLimitRule;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.repository.service.BaseEntitySystemServiceImpl;
import lombok.Getter;
import lombok.Setter;

/**
 * System service implementation for RateLimitRule entity (bypasses permission checks).
 */
@FrameworkComponent
public class RateLimitRuleSystemServiceImpl extends BaseEntitySystemServiceImpl<RateLimitRule> implements RateLimitRuleSystemApi {

    @Inject
    @Getter
    @Setter
    private RateLimitRuleRepository repository;

    public RateLimitRuleSystemServiceImpl() {
        super(RateLimitRule.class);
    }
}
