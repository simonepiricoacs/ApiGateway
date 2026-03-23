package it.water.infrastructure.apigateway.repository;

import it.water.infrastructure.apigateway.api.RouteRepository;
import it.water.infrastructure.apigateway.model.Route;
import it.water.core.api.repository.query.Query;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.repository.entity.model.exceptions.NoResultException;
import it.water.repository.jpa.WaterJpaRepositoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * JPA Repository implementation for Route entity.
 */
@FrameworkComponent
public class RouteRepositoryImpl extends WaterJpaRepositoryImpl<Route> implements RouteRepository {

    private static final Logger log = LoggerFactory.getLogger(RouteRepositoryImpl.class);
    private static final String ROUTE_PERSISTENCE_UNIT = "api-gateway-persistence-unit";

    public RouteRepositoryImpl() {
        super(Route.class, ROUTE_PERSISTENCE_UNIT);
    }

    @Override
    public Route findByRouteId(String routeId) {
        log.debug("Finding route by routeId: {}", routeId);
        Query query = getQueryBuilderInstance().field("routeId").equalTo(routeId);
        try {
            return find(query);
        } catch (NoResultException e) {
            log.debug("No route found with routeId: {}", routeId);
            return null;
        }
    }

    @Override
    public List<Route> findByTargetServiceName(String targetServiceName) {
        log.debug("Finding routes by targetServiceName: {}", targetServiceName);
        Query query = getQueryBuilderInstance().field("targetServiceName").equalTo(targetServiceName);
        return findAll(-1, 1, query, null).getResults().stream().toList();
    }

    @Override
    public List<Route> findByEnabled(boolean enabled) {
        log.debug("Finding routes by enabled: {}", enabled);
        Query query = getQueryBuilderInstance().createQueryFilter("enabled=" + enabled);
        return findAll(-1, 1, query, null).getResults().stream().toList();
    }

    @Override
    public List<Route> findOrderedByPriority() {
        log.debug("Finding all routes ordered by priority");
        return findAll(-1, 1, null, null).getResults().stream()
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .toList();
    }
}
