package it.water.infrastructure.apigateway;

import it.water.infrastructure.apigateway.api.GatewayAuthenticationApi;
import it.water.infrastructure.apigateway.model.*;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Unit tests for Gateway Authentication service.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayAuthenticationApiTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private GatewayAuthenticationApi gatewayAuthenticationApi;

    @BeforeAll
    void beforeAll() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(1)
    void componentInstantiatedCorrectly() {
        Assertions.assertNotNull(gatewayAuthenticationApi);
    }

    @Test
    @Order(2)
    void noAuthHeaderReturnsPassthrough() {
        GatewayRequest request = buildRequest(null, null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertFalse(result.isAuthenticated());
        Assertions.assertEquals(AuthMethod.PASSTHROUGH, result.getMethod());
    }

    @Test
    @Order(3)
    void validApiKeyReturnsAuthenticated() {
        ApiKeyConfig config = ApiKeyConfig.builder()
                .apiKey("test-api-key-123")
                .description("Test API Key")
                .enabled(true)
                .allowedPaths(List.of("/api/**"))
                .build();
        gatewayAuthenticationApi.registerApiKey("test-api-key-123", config);

        GatewayRequest request = buildRequest(null, "test-api-key-123");
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertTrue(result.isAuthenticated());
        Assertions.assertEquals(AuthMethod.API_KEY, result.getMethod());
        Assertions.assertEquals("test-api-key-123", result.getApiKey());
    }

    @Test
    @Order(4)
    void revokedApiKeyNotAuthenticated() {
        ApiKeyConfig config = ApiKeyConfig.builder()
                .apiKey("revoked-key")
                .description("Revoked Key")
                .enabled(true)
                .build();
        gatewayAuthenticationApi.registerApiKey("revoked-key", config);
        gatewayAuthenticationApi.revokeApiKey("revoked-key");

        GatewayRequest request = buildRequest(null, "revoked-key");
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertFalse(result.isAuthenticated());
    }

    @Test
    @Order(5)
    void expiredApiKeyNotAuthenticated() {
        ApiKeyConfig config = ApiKeyConfig.builder()
                .apiKey("expired-key")
                .description("Expired Key")
                .enabled(true)
                .expiresAt(new Date(System.currentTimeMillis() - 1000)) // expired 1 second ago
                .build();
        gatewayAuthenticationApi.registerApiKey("expired-key", config);

        Assertions.assertFalse(gatewayAuthenticationApi.validateApiKey("expired-key"));
    }

    @Test
    @Order(6)
    void validBasicAuthReturnsAuthenticated() {
        String credentials = Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8));
        GatewayRequest request = buildRequest("Basic " + credentials, null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertTrue(result.isAuthenticated());
        Assertions.assertEquals(AuthMethod.BASIC_AUTH, result.getMethod());
        Assertions.assertEquals("user", result.getUserId());
    }

    @Test
    @Order(7)
    void invalidBasicAuthNotAuthenticated() {
        GatewayRequest request = buildRequest("Basic invalid-base64!!!", null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertFalse(result.isAuthenticated());
    }

    @Test
    @Order(8)
    void invalidJwtNotAuthenticated() {
        GatewayRequest request = buildRequest("Bearer not-a-jwt", null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertFalse(result.isAuthenticated());
        Assertions.assertEquals(AuthMethod.JWT_VALIDATION, result.getMethod());
    }

    @Test
    @Order(9)
    void validateApiKeyReturnsTrueForValidKey() {
        ApiKeyConfig config = ApiKeyConfig.builder()
                .apiKey("validate-test-key")
                .enabled(true)
                .build();
        gatewayAuthenticationApi.registerApiKey("validate-test-key", config);
        Assertions.assertTrue(gatewayAuthenticationApi.validateApiKey("validate-test-key"));
    }

    @Test
    @Order(10)
    void validateApiKeyReturnsFalseForUnknownKey() {
        Assertions.assertFalse(gatewayAuthenticationApi.validateApiKey("unknown-key-xyz"));
    }

    @Test
    @Order(11)
    void validJwtWithSubAndNoExpAuthenticates() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"user-jwt-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = header + "." + payload + ".signature";
        GatewayRequest request = buildRequest("Bearer " + token, null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertTrue(result.isAuthenticated());
        Assertions.assertEquals(AuthMethod.JWT_VALIDATION, result.getMethod());
        Assertions.assertEquals("user-jwt-1", result.getUserId());
    }

    @Test
    @Order(12)
    void expiredJwtNotAuthenticated() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // exp=1 is 1970-01-01 → always expired
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"user-jwt-exp\",\"exp\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = header + "." + payload + ".signature";
        GatewayRequest request = buildRequest("Bearer " + token, null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertFalse(result.isAuthenticated());
        Assertions.assertEquals(AuthMethod.JWT_VALIDATION, result.getMethod());
    }

    @Test
    @Order(13)
    void jwtWithFutureExpAuthenticates() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // exp=9999999999 → year 2286 → always valid
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"user-jwt-future\",\"exp\":9999999999}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = header + "." + payload + ".signature";
        GatewayRequest request = buildRequest("Bearer " + token, null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertTrue(result.isAuthenticated());
        Assertions.assertEquals("user-jwt-future", result.getUserId());
    }

    @Test
    @Order(14)
    void jwtWithTwoPartsOnlyNotAuthenticated() {
        // Only 2 parts instead of 3 → invalid format
        String token = "part1.part2";
        GatewayRequest request = buildRequest("Bearer " + token, null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertFalse(result.isAuthenticated());
        Assertions.assertEquals(AuthMethod.JWT_VALIDATION, result.getMethod());
    }

    @Test
    @Order(15)
    void disabledApiKeyNotAuthenticated() {
        ApiKeyConfig config = ApiKeyConfig.builder()
                .apiKey("disabled-api-key")
                .description("Disabled key")
                .enabled(false)
                .build();
        gatewayAuthenticationApi.registerApiKey("disabled-api-key", config);
        GatewayRequest request = buildRequest(null, "disabled-api-key");
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertFalse(result.isAuthenticated());
    }

    @Test
    @Order(16)
    void unknownAuthSchemeReturnsPassthrough() {
        GatewayRequest request = buildRequest("Digest realm=\"example\"", null);
        AuthResult result = gatewayAuthenticationApi.authenticate(request);
        Assertions.assertFalse(result.isAuthenticated());
        Assertions.assertEquals(AuthMethod.PASSTHROUGH, result.getMethod());
    }

    private GatewayRequest buildRequest(String authHeader, String apiKeyHeader) {
        GatewayRequest request = GatewayRequest.builder()
                .method(HttpMethod.GET)
                .path("/api/test")
                .clientIp("10.0.0.1")
                .build();
        if (authHeader != null) {
            request.getHeaders().put("Authorization", authHeader);
        }
        if (apiKeyHeader != null) {
            request.getHeaders().put("X-API-KEY", apiKeyHeader);
        }
        return request;
    }
}
