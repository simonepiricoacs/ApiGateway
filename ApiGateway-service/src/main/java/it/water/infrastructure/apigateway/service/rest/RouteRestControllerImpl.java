package it.water.infrastructure.apigateway.service.rest;

import it.water.infrastructure.apigateway.api.RouteApi;
import it.water.infrastructure.apigateway.api.rest.RouteRestApi;
import it.water.infrastructure.apigateway.model.Route;
import it.water.core.api.service.BaseEntityApi;
import it.water.core.api.service.rest.FrameworkRestController;
import it.water.core.interceptors.annotations.Inject;
import it.water.service.rest.persistence.BaseEntityRestApi;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller implementation for Route entity.
 */
@FrameworkRestController(referredRestApi = RouteRestApi.class)
public class RouteRestControllerImpl extends BaseEntityRestApi<Route> implements RouteRestApi {

    @SuppressWarnings("java:S1068")
    private static final Logger log = LoggerFactory.getLogger(RouteRestControllerImpl.class);

    @Inject
    @Setter
    private RouteApi routeApi;

    @Override
    protected BaseEntityApi<Route> getEntityService() {
        return routeApi;
    }

    @Override
    public void refreshRoutes() {
        log.debug("REST: refreshing routes");
        routeApi.refreshRoutes();
    }
}
