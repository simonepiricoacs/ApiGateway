package it.water.infrastructure.apigateway.service.rest.spring;

import it.water.infrastructure.apigateway.model.RateLimitRule;
import it.water.infrastructure.apigateway.service.rest.RateLimitRuleRestControllerImpl;
import it.water.core.api.model.PaginableResult;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring REST controller for RateLimitRule.
 */
@RestController
public class RateLimitRuleSpringRestControllerImpl extends RateLimitRuleRestControllerImpl implements RateLimitRuleSpringRestApi {

    @Override
    @SuppressWarnings("java:S1185")
    public RateLimitRule save(RateLimitRule entity) {
        return super.save(entity);
    }

    @Override
    @SuppressWarnings("java:S1185")
    public RateLimitRule update(RateLimitRule entity) {
        return super.update(entity);
    }

    @Override
    @SuppressWarnings("java:S1185")
    public void remove(long id) {
        super.remove(id);
    }

    @Override
    @SuppressWarnings("java:S1185")
    public RateLimitRule find(long id) {
        return super.find(id);
    }

    @Override
    @SuppressWarnings("java:S1185")
    public PaginableResult<RateLimitRule> findAll() {
        return super.findAll();
    }
}
