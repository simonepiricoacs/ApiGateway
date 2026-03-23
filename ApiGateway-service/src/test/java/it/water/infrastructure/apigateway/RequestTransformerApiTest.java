package it.water.infrastructure.apigateway;

import it.water.infrastructure.apigateway.api.RequestTransformerApi;
import it.water.infrastructure.apigateway.model.*;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for Request Transformer service.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequestTransformerApiTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private RequestTransformerApi requestTransformerApi;

    @BeforeAll
    void beforeAll() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(1)
    void componentInstantiatedCorrectly() {
        Assertions.assertNotNull(requestTransformerApi);
    }

    @Test
    @Order(2)
    void addHeaderFilter() {
        Route route = buildRouteWithFilters(Map.of("addHeader.X-Custom-Header", "custom-value"));
        GatewayRequest request = buildRequest("/api/test");
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, route);
        Assertions.assertEquals("custom-value", transformed.getHeaders().get("X-Custom-Header"));
    }

    @Test
    @Order(3)
    void removeHeaderFilter() {
        Route route = buildRouteWithFilters(Map.of("removeHeader", "X-Remove-Me"));
        GatewayRequest request = buildRequest("/api/test");
        request.getHeaders().put("X-Remove-Me", "to-be-removed");
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, route);
        Assertions.assertNull(transformed.getHeaders().get("X-Remove-Me"));
    }

    @Test
    @Order(4)
    void modifyHeaderFilter() {
        Route route = buildRouteWithFilters(Map.of("modifyHeader.X-Existing", "modified-value"));
        GatewayRequest request = buildRequest("/api/test");
        request.getHeaders().put("X-Existing", "original-value");
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, route);
        Assertions.assertEquals("modified-value", transformed.getHeaders().get("X-Existing"));
    }

    @Test
    @Order(5)
    void addQueryParamFilter() {
        Route route = buildRouteWithFilters(Map.of("addParam.version", "v2"));
        GatewayRequest request = buildRequest("/api/test");
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, route);
        Assertions.assertNotNull(transformed.getQueryString());
        Assertions.assertTrue(transformed.getQueryString().contains("version=v2"));
    }

    @Test
    @Order(6)
    void pathRewriteWithStaticPath() {
        Route route = new Route("rewrite-route", "/old/**", HttpMethod.ANY, "backend", 1, true);
        route.setRewritePath("/new/$1");
        GatewayRequest request = buildRequest("/old/resource/123");
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, route);
        Assertions.assertNotNull(transformed.getPath());
    }

    @Test
    @Order(7)
    void nullRouteReturnsOriginalRequest() {
        GatewayRequest request = buildRequest("/api/test");
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, null);
        Assertions.assertEquals(request, transformed);
    }

    @Test
    @Order(8)
    void nullRequestReturnsNull() {
        Route route = buildRouteWithFilters(Map.of());
        GatewayRequest transformed = requestTransformerApi.transformRequest(null, route);
        Assertions.assertNull(transformed);
    }

    @Test
    @Order(9)
    void transformResponseAddResponseHeader() {
        Route route = buildRouteWithFilters(Map.of("addResponseHeader.X-Gateway", "powered-by-water"));
        GatewayResponse response = GatewayResponse.builder()
                .statusCode(200)
                .body("OK".getBytes())
                .build();
        GatewayResponse transformed = requestTransformerApi.transformResponse(response, route);
        Assertions.assertEquals("powered-by-water", transformed.getHeaders().get("X-Gateway"));
    }

    @Test
    @Order(10)
    void originalRequestHeadersPreserved() {
        Route route = buildRouteWithFilters(Map.of("addHeader.New-Header", "new-value"));
        GatewayRequest request = buildRequest("/api/test");
        request.getHeaders().put("Existing-Header", "existing-value");
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, route);
        Assertions.assertEquals("existing-value", transformed.getHeaders().get("Existing-Header"));
        Assertions.assertEquals("new-value", transformed.getHeaders().get("New-Header"));
    }

    @Test
    @Order(11)
    void transformResponseNullRouteReturnsOriginalResponse() {
        GatewayResponse response = GatewayResponse.builder().statusCode(200).build();
        GatewayResponse result = requestTransformerApi.transformResponse(response, null);
        Assertions.assertEquals(response, result);
    }

    @Test
    @Order(12)
    void transformResponseNullResponseReturnsNull() {
        Route route = buildRouteWithFilters(Map.of("addResponseHeader.X-Test", "value"));
        GatewayResponse result = requestTransformerApi.transformResponse(null, route);
        Assertions.assertNull(result);
    }

    @Test
    @Order(13)
    void transformResponseWithNullFiltersReturnsOriginalResponse() {
        Route route = new Route("no-filters-route", "/api/**", HttpMethod.ANY, "svc", 1, true);
        // filters are null (not set)
        GatewayResponse response = GatewayResponse.builder().statusCode(200).build();
        GatewayResponse result = requestTransformerApi.transformResponse(response, route);
        Assertions.assertEquals(response, result);
    }

    @Test
    @Order(14)
    void transformResponseRemovesResponseHeaderWhenConfigured() {
        Route route = buildRouteWithFilters(new HashMap<>(Map.of("removeResponseHeader", "X-Remove-Response")));
        GatewayResponse response = GatewayResponse.builder().statusCode(200).build();
        response.getHeaders().put("X-Remove-Response", "old-value");
        GatewayResponse transformed = requestTransformerApi.transformResponse(response, route);
        Assertions.assertNull(transformed.getHeaders().get("X-Remove-Response"));
    }

    @Test
    @Order(15)
    void pathRewriteReturnsOriginalPathWhenPatternDoesNotMatch() {
        Route route = new Route("no-match-rewrite", "/specific/exact/path", HttpMethod.ANY, "svc", 1, true);
        route.setRewritePath("/rewritten/$1");
        // Request path does not match the pattern
        GatewayRequest request = buildRequest("/totally/different/path");
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, route);
        Assertions.assertEquals("/totally/different/path", transformed.getPath());
    }

    @Test
    @Order(16)
    void addQueryParamFilterAppendsToExistingQueryString() {
        Route route = buildRouteWithFilters(Map.of("addParam.format", "json"));
        GatewayRequest request = GatewayRequest.builder()
                .method(HttpMethod.GET)
                .path("/api/test")
                .queryString("page=1")
                .clientIp("10.0.0.1")
                .build();
        GatewayRequest transformed = requestTransformerApi.transformRequest(request, route);
        Assertions.assertNotNull(transformed.getQueryString());
        Assertions.assertTrue(transformed.getQueryString().contains("page=1"));
        Assertions.assertTrue(transformed.getQueryString().contains("format=json"));
        Assertions.assertTrue(transformed.getQueryString().contains("&"));
    }

    private Route buildRouteWithFilters(Map<String, String> filters) {
        Route route = new Route("test-route", "/api/**", HttpMethod.ANY, "test-service", 1, true);
        route.setFilters(new HashMap<>(filters));
        return route;
    }

    private GatewayRequest buildRequest(String path) {
        return GatewayRequest.builder()
                .method(HttpMethod.GET)
                .path(path)
                .clientIp("10.0.0.1")
                .build();
    }
}
