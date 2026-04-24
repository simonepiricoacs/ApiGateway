package it.water.infrastructure.apigateway.service.rest;

import it.water.core.api.service.rest.FrameworkRestController;
import it.water.core.interceptors.annotations.Inject;
import it.water.infrastructure.apigateway.api.GatewayRouterApi;
import it.water.infrastructure.apigateway.api.rest.GatewayProxyRestApi;
import it.water.infrastructure.apigateway.model.GatewayRequest;
import it.water.infrastructure.apigateway.model.GatewayResponse;
import it.water.infrastructure.apigateway.model.HttpMethod;
import lombok.Setter;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller exposing a real HTTP gateway entrypoint under /water/proxy/*.
 */
@FrameworkRestController(referredRestApi = GatewayProxyRestApi.class)
public class GatewayProxyRestControllerImpl implements GatewayProxyRestApi {

    @Inject
    @Setter
    private GatewayRouterApi gatewayRouterApi;

    @Override
    public Response proxyGet(String path, HttpHeaders headers, UriInfo uriInfo) {
        return proxy(HttpMethod.GET, path, null, headers, uriInfo);
    }

    @Override
    public Response proxyPost(String path, byte[] body, HttpHeaders headers, UriInfo uriInfo) {
        return proxy(HttpMethod.POST, path, body, headers, uriInfo);
    }

    @Override
    public Response proxyPut(String path, byte[] body, HttpHeaders headers, UriInfo uriInfo) {
        return proxy(HttpMethod.PUT, path, body, headers, uriInfo);
    }

    public Response proxyDelete(String path, byte[] body, HttpHeaders headers, UriInfo uriInfo) {
        return proxy(HttpMethod.DELETE, path, body, headers, uriInfo);
    }

    @Override
    public Response proxyOptions(String path, byte[] body, HttpHeaders headers, UriInfo uriInfo) {
        return proxy(HttpMethod.OPTIONS, path, body, headers, uriInfo);
    }

    @Override
    public Response proxyHead(String path, HttpHeaders headers, UriInfo uriInfo) {
        return proxy(HttpMethod.HEAD, path, null, headers, uriInfo);
    }

    private Response proxy(HttpMethod method, String path, byte[] body, HttpHeaders headers, UriInfo uriInfo) {
        GatewayResponse gatewayResponse = gatewayRouterApi.route(buildGatewayRequest(method, path, body, headers, uriInfo));
        Response.ResponseBuilder builder = Response.status(gatewayResponse.getStatusCode());
        gatewayResponse.getHeaders().forEach(builder::header);
        if (method != HttpMethod.HEAD && gatewayResponse.getBody() != null) {
            builder.entity(gatewayResponse.getBody());
        }
        return builder.build();
    }

    private GatewayRequest buildGatewayRequest(HttpMethod method, String path, byte[] body, HttpHeaders headers, UriInfo uriInfo) {
        return GatewayRequest.builder()
                .method(method)
                .path(normalizePath(path))
                .queryString(uriInfo.getRequestUri().getRawQuery())
                .headers(extractHeaders(headers))
                .body(body == null || body.length == 0 ? null : body)
                .clientIp(extractClientIp(headers))
                .protocol(uriInfo.getRequestUri().getScheme())
                .build();
    }

    private Map<String, String> extractHeaders(HttpHeaders requestHeaders) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, java.util.List<String>> header : requestHeaders.getRequestHeaders().entrySet()) {
            if (!header.getValue().isEmpty()) {
                headers.put(header.getKey(), header.getValue().get(0));
            }
        }
        return headers;
    }

    private String extractClientIp(HttpHeaders headers) {
        String forwardedFor = headers.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = headers.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return "unknown";
    }

    private String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
