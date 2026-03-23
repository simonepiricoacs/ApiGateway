package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.RouteRepository;
import it.water.infrastructure.apigateway.api.RouteSystemApi;
import it.water.infrastructure.apigateway.model.Route;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.repository.service.BaseEntitySystemServiceImpl;
import lombok.Getter;
import lombok.Setter;

/**
 * System service implementation for Route entity (bypasses permission checks).
 */
@FrameworkComponent
public class RouteSystemServiceImpl extends BaseEntitySystemServiceImpl<Route> implements RouteSystemApi {

    @Inject
    @Getter
    @Setter
    private RouteRepository repository;

    public RouteSystemServiceImpl() {
        super(Route.class);
    }
}
