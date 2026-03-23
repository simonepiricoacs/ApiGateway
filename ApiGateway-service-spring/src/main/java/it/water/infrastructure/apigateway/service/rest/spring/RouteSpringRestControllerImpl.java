package it.water.infrastructure.apigateway.service.rest.spring;

import it.water.infrastructure.apigateway.model.Route;
import it.water.infrastructure.apigateway.service.rest.RouteRestControllerImpl;
import it.water.core.api.model.PaginableResult;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring REST controller for Route - delegates to service layer.
 */
@RestController
public class RouteSpringRestControllerImpl extends RouteRestControllerImpl implements RouteSpringRestApi {

    @Override
    @SuppressWarnings("java:S1185")
    public Route save(Route entity) {
        return super.save(entity);
    }

    @Override
    @SuppressWarnings("java:S1185")
    public Route update(Route entity) {
        return super.update(entity);
    }

    @Override
    @SuppressWarnings("java:S1185")
    public void remove(long id) {
        super.remove(id);
    }

    @Override
    @SuppressWarnings("java:S1185")
    public Route find(long id) {
        return super.find(id);
    }

    @Override
    @SuppressWarnings("java:S1185")
    public PaginableResult<Route> findAll() {
        return super.findAll();
    }

    @Override
    @SuppressWarnings("java:S1185")
    public void refreshRoutes() {
        super.refreshRoutes();
    }
}
