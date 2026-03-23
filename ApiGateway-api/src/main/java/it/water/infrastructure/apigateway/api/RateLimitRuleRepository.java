package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.RateLimitRule;
import it.water.core.api.repository.BaseRepository;

import java.util.List;

/**
 * Repository interface for RateLimitRule entity.
 */
public interface RateLimitRuleRepository extends BaseRepository<RateLimitRule> {
    RateLimitRule findByRuleId(String ruleId);
    List<RateLimitRule> findByEnabled(boolean enabled);
}
