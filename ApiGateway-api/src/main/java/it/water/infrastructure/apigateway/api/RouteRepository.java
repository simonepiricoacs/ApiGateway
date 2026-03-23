package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.Route;
import it.water.core.api.repository.BaseRepository;

import java.util.List;

/**
 * Repository interface for Route entity.
 */
public interface RouteRepository extends BaseRepository<Route> {
    Route findByRouteId(String routeId);
    List<Route> findByTargetServiceName(String targetServiceName);
    List<Route> findByEnabled(boolean enabled);
    List<Route> findOrderedByPriority();
}
