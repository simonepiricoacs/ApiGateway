package it.water.infrastructure.apigateway.api;

import it.water.infrastructure.apigateway.model.ApiKeyConfig;
import it.water.infrastructure.apigateway.model.AuthResult;
import it.water.infrastructure.apigateway.model.GatewayRequest;
import it.water.core.api.service.Service;

/**
 * Gateway authentication API - validates incoming request credentials.
 */
public interface GatewayAuthenticationApi extends Service {
    AuthResult authenticate(GatewayRequest request);
    void registerApiKey(String apiKey, ApiKeyConfig config);
    void revokeApiKey(String apiKey);
    boolean validateApiKey(String apiKey);
}
