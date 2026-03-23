package it.water.infrastructure.apigateway.repository;

import it.water.infrastructure.apigateway.api.RateLimitRuleRepository;
import it.water.infrastructure.apigateway.model.RateLimitRule;
import it.water.core.api.repository.query.Query;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.repository.entity.model.exceptions.NoResultException;
import it.water.repository.jpa.WaterJpaRepositoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * JPA Repository implementation for RateLimitRule entity.
 */
@FrameworkComponent
public class RateLimitRuleRepositoryImpl extends WaterJpaRepositoryImpl<RateLimitRule> implements RateLimitRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(RateLimitRuleRepositoryImpl.class);
    private static final String RATE_LIMIT_PERSISTENCE_UNIT = "api-gateway-persistence-unit";

    public RateLimitRuleRepositoryImpl() {
        super(RateLimitRule.class, RATE_LIMIT_PERSISTENCE_UNIT);
    }

    @Override
    public RateLimitRule findByRuleId(String ruleId) {
        log.debug("Finding rate limit rule by ruleId: {}", ruleId);
        Query query = getQueryBuilderInstance().field("ruleId").equalTo(ruleId);
        try {
            return find(query);
        } catch (NoResultException e) {
            log.debug("No rate limit rule found with ruleId: {}", ruleId);
            return null;
        }
    }

    @Override
    public List<RateLimitRule> findByEnabled(boolean enabled) {
        log.debug("Finding rate limit rules by enabled: {}", enabled);
        Query query = getQueryBuilderInstance().createQueryFilter("enabled=" + enabled);
        return findAll(-1, 1, query, null).getResults().stream().toList();
    }
}
