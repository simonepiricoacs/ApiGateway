package it.water.infrastructure.apigateway.actions;

/**
 * Actions available for ApiGateway entities.
 */
public class ApiGatewayActions {
    public static final String CONFIGURE_RATE_LIMIT = "configure-rate-limit";
    public static final String MANAGE_CIRCUIT_BREAKER = "manage-circuit-breaker";
    public static final String VIEW_METRICS = "view-metrics";
    public static final String PROXY_REQUEST = "proxy-request";
    public static final String REFRESH_ROUTES = "refresh-routes";

    private ApiGatewayActions() {
    }
}
