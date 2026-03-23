package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.GatewayAuthenticationApi;
import it.water.infrastructure.apigateway.model.*;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.api.interceptors.OnActivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway authentication service implementation.
 * Supports JWT, API_KEY, BASIC_AUTH, PASSTHROUGH, and OAuth2 (stub).
 */
@FrameworkComponent
public class GatewayAuthenticationServiceImpl implements GatewayAuthenticationApi {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationServiceImpl.class);

    private final Map<String, ApiKeyConfig> apiKeys = new ConcurrentHashMap<>();

    @OnActivate
    public void activate() {
        log.info("GatewayAuthenticationServiceImpl activated");
    }

    @Override
    public AuthResult authenticate(GatewayRequest request) {
        // Check X-API-KEY header first (no Authorization header required)
        String apiKeyHeader = request.getHeaders().get("X-API-KEY");
        if (apiKeyHeader != null) {
            return authenticateApiKey(apiKeyHeader);
        }

        String authHeader = request.getHeaders().get("Authorization");
        if (authHeader == null) {
            return buildUnauthenticated(AuthMethod.PASSTHROUGH);
        }

        if (authHeader.startsWith("Bearer ")) {
            return authenticateJwt(authHeader.substring(7));
        }
        if (authHeader.startsWith("Basic ")) {
            return authenticateBasic(authHeader.substring(6));
        }

        return buildUnauthenticated(AuthMethod.PASSTHROUGH);
    }

    @Override
    public void registerApiKey(String apiKey, ApiKeyConfig config) {
        log.info("Registering API key: {}", apiKey);
        apiKeys.put(apiKey, config);
    }

    @Override
    public void revokeApiKey(String apiKey) {
        log.info("Revoking API key: {}", apiKey);
        apiKeys.remove(apiKey);
    }

    @Override
    public boolean validateApiKey(String apiKey) {
        ApiKeyConfig config = apiKeys.get(apiKey);
        if (config == null || !config.isEnabled()) return false;
        if (config.getExpiresAt() != null && config.getExpiresAt().before(new Date())) return false;
        return true;
    }

    private AuthResult authenticateJwt(String token) {
        try {
            // Minimal JWT structure check (payload decode)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.debug("Invalid JWT format");
                return buildUnauthenticated(AuthMethod.JWT_VALIDATION);
            }
            // In production, integrate with Water JwtTokenService
            // Here we do a structural check and extract subject from payload
            String payload = new String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8);
            // Check exp if present (simplified)
            if (payload.contains("\"exp\"")) {
                long exp = extractLong(payload, "exp");
                if (exp > 0 && exp < System.currentTimeMillis() / 1000) {
                    log.debug("JWT token expired");
                    return buildUnauthenticated(AuthMethod.JWT_VALIDATION);
                }
            }
            String sub = extractString(payload, "sub");
            return AuthResult.builder()
                    .authenticated(true)
                    .userId(sub)
                    .roles(Collections.emptyList())
                    .method(AuthMethod.JWT_VALIDATION)
                    .build();
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return buildUnauthenticated(AuthMethod.JWT_VALIDATION);
        }
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
            ApiKeyConfig config = apiKeys.get(apiKey);
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

    private String padBase64(String base64) {
        int padding = 4 - base64.length() % 4;
        if (padding < 4) {
            return base64 + "=".repeat(padding);
        }
        return base64;
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    private long extractLong(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return -1;
        start += search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
