package it.water.infrastructure.apigateway.model;

import it.water.service.discovery.model.ServiceRegistration;
import lombok.*;

/**
 * Result of a route matching operation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteResult {
    private Route route;
    private ServiceRegistration selectedInstance;
    private GatewayRequest transformedRequest;
}
