package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.GatewayRequest;
import it.water.infrastructure.apigateway.model.GatewayResponse;
import it.water.infrastructure.apigateway.model.Route;
import it.water.infrastructure.apigateway.model.RouteResult;
import it.water.core.api.service.Service;

import java.util.List;

/**
 * Core gateway router API - routes requests to upstream services.
 */
public interface GatewayRouterApi extends Service {
    GatewayResponse route(GatewayRequest request);
    List<Route> getActiveRoutes();
    void refreshRoutes();
    void addDynamicRoute(Route route);
    void removeDynamicRoute(String routeId);
    RouteResult resolveRoute(GatewayRequest request);
}
