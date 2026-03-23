package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.Route;
import it.water.core.api.service.BaseEntityApi;

import java.util.List;

/**
 * API interface for Route entity with permission checks.
 */
public interface RouteApi extends BaseEntityApi<Route> {
    void addDynamicRoute(Route route);
    void removeDynamicRoute(String routeId);
    List<Route> getActiveRoutes();
    void refreshRoutes();
}
