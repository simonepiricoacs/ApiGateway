package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.Route;
import it.water.core.api.service.BaseEntitySystemApi;

/**
 * System API for Route entity - bypasses permission checks.
 */
public interface RouteSystemApi extends BaseEntitySystemApi<Route> {
}
