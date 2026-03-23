package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.GatewayRequest;
import it.water.infrastructure.apigateway.model.GatewayResponse;
import it.water.infrastructure.apigateway.model.Route;
import it.water.core.api.service.Service;

/**
 * Request/Response transformer API - modifies requests and responses in transit.
 */
public interface RequestTransformerApi extends Service {
    GatewayRequest transformRequest(GatewayRequest request, Route route);
    GatewayResponse transformResponse(GatewayResponse response, Route route);
}
