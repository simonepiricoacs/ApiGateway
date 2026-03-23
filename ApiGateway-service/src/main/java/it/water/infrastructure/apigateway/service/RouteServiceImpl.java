package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.RouteApi;
import it.water.infrastructure.apigateway.api.RouteRepository;
import it.water.infrastructure.apigateway.api.RouteSystemApi;
import it.water.infrastructure.apigateway.model.Route;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.repository.service.BaseEntityServiceImpl;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Service implementation for Route entity.
 */
@FrameworkComponent
public class RouteServiceImpl extends BaseEntityServiceImpl<Route> implements RouteApi {

    @Inject
    @Getter
    @Setter
    private RouteSystemApi systemService;

    @Inject
    @Getter
    @Setter
    private RouteRepository repository;

    @Inject
    @Getter
    @Setter
    private ComponentRegistry componentRegistry;

    public RouteServiceImpl() {
        super(Route.class);
    }

    @Override
    public void addDynamicRoute(Route route) {
        getLog().debug("Adding dynamic route: {}", route.getRouteId());
        systemService.save(route);
    }

    @Override
    public void removeDynamicRoute(String routeId) {
        getLog().debug("Removing dynamic route: {}", routeId);
        Route route = repository.findByRouteId(routeId);
        if (route != null) {
            systemService.remove(route.getId());
        }
    }

    @Override
    public List<Route> getActiveRoutes() {
        getLog().debug("Getting all active routes");
        return repository.findByEnabled(true);
    }

    @Override
    public void refreshRoutes() {
        getLog().debug("Refreshing routes from database");
        // Routes are loaded fresh from DB on each call to getActiveRoutes
        // This method can trigger cache refresh in GatewayRouterApi
    }
}
