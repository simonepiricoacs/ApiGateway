package it.water.infrastructure.apigateway.service;

import it.water.core.api.interceptors.OnActivate;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.infrastructure.apigateway.api.GatewayAuthenticationApi;
import it.water.infrastructure.apigateway.model.ApiKeyConfig;
import it.water.infrastructure.apigateway.model.AuthMethod;
import it.water.infrastructure.apigateway.model.AuthResult;
import it.water.infrastructure.apigateway.model.GatewayRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reserved gateway authentication component.
 *
 * <p>The current gateway proxy path is protected by Water REST security
 * annotations and does not call this component. It is kept as a future extension
 * point for gateway-specific authentication, but JWT validation is deliberately
 * fail-closed until this class delegates to the real Water JwtTokenService.
 */
@FrameworkComponent
public class GatewayAuthenticationServiceImpl implements GatewayAuthenticationApi {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationServiceImpl.class);

    private final Map<String, ApiKeyConfig> apiKeys = new ConcurrentHashMap<>();

    @OnActivate
    public void activate() {
        log.info("GatewayAuthenticationServiceImpl activated as reserved auth component");
    }

    @Override
    public AuthResult authenticate(GatewayRequest request) {
        String apiKeyHeader = request.getHeaders().get("X-API-KEY");
        if (apiKeyHeader != null) {
            return authenticateApiKey(apiKeyHeader);
        }

        String authHeader = request.getHeaders().get("Authorization");
        if (authHeader == null) {
            return buildUnauthenticated(AuthMethod.PASSTHROUGH);
        }

        if (authHeader.startsWith("Bearer ")) {
            return authenticateJwt();
        }
        if (authHeader.startsWith("Basic ")) {
            return authenticateBasic(authHeader.substring(6));
        }

        return buildUnauthenticated(AuthMethod.PASSTHROUGH);
    }

    @Override
    public void registerApiKey(String apiKey, ApiKeyConfig config) {
        log.info("Registering reserved API key: {}", apiKey);
        apiKeys.put(apiKey, config);
    }

    @Override
    public void revokeApiKey(String apiKey) {
        log.info("Revoking reserved API key: {}", apiKey);
        apiKeys.remove(apiKey);
    }

    @Override
    public boolean validateApiKey(String apiKey) {
        ApiKeyConfig config = apiKeys.get(apiKey);
        if (config == null || !config.isEnabled()) {
            return false;
        }
        return config.getExpiresAt() == null || !config.getExpiresAt().before(new Date());
    }

    private AuthResult authenticateJwt() {
        log.debug("JWT authentication is disabled until integrated with Water JwtTokenService");
        return buildUnauthenticated(AuthMethod.JWT_VALIDATION);
    }

    private AuthResult authenticateBasic(String encoded) {
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            if (parts.length == 2) {
                return AuthResult.builder()
                        .authenticated(true)
                        .userId(parts[0])
                        .roles(Collections.emptyList())
                        .method(AuthMethod.BASIC_AUTH)
                        .build();
            }
        } catch (Exception e) {
            log.debug("Basic auth decode failed: {}", e.getMessage());
        }
        return buildUnauthenticated(AuthMethod.BASIC_AUTH);
    }

    private AuthResult authenticateApiKey(String apiKey) {
        if (validateApiKey(apiKey)) {
            return AuthResult.builder()
                    .authenticated(true)
                    .userId("apikey:" + apiKey)
                    .apiKey(apiKey)
                    .roles(Collections.emptyList())
                    .method(AuthMethod.API_KEY)
                    .build();
        }
        return buildUnauthenticated(AuthMethod.API_KEY);
    }

    private AuthResult buildUnauthenticated(AuthMethod method) {
        return AuthResult.builder()
                .authenticated(false)
                .method(method)
                .roles(Collections.emptyList())
                .build();
    }
}
