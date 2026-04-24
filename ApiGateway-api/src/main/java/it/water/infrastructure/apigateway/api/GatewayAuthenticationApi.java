package it.water.infrastructure.apigateway.api;

import it.water.core.api.service.Service;
import it.water.infrastructure.apigateway.model.ApiKeyConfig;
import it.water.infrastructure.apigateway.model.AuthResult;
import it.water.infrastructure.apigateway.model.GatewayRequest;

/**
 * Reserved gateway authentication API.
 *
 * <p>This contract is intentionally kept for the future gateway-authentication
 * layer, but the current proxy path does not invoke it. JWT validation must not
 * be implemented locally: the service implementation has to delegate to the
 * real Water JWT infrastructure before it can be used in production routing.
 */
public interface GatewayAuthenticationApi extends Service {
    AuthResult authenticate(GatewayRequest request);
    void registerApiKey(String apiKey, ApiKeyConfig config);
    void revokeApiKey(String apiKey);
    boolean validateApiKey(String apiKey);
}
