package it.water.infrastructure.apigateway.service.rest.spring;

import it.water.infrastructure.apigateway.api.GatewayRouterApi;
import it.water.infrastructure.apigateway.model.GatewayRequest;
import it.water.infrastructure.apigateway.model.GatewayResponse;
import it.water.infrastructure.apigateway.model.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring MVC controller exposing the proxy endpoint under /water/proxy/*.
 */
@RestController
public class GatewayProxySpringRestControllerImpl implements GatewayProxySpringRestApi {

    private final GatewayRouterApi gatewayRouterApi;

    public GatewayProxySpringRestControllerImpl(GatewayRouterApi gatewayRouterApi) {
        this.gatewayRouterApi = gatewayRouterApi;
    }

    @Override
    public ResponseEntity<byte[]> proxy(String path, HttpHeaders headers, byte[] body, HttpServletRequest request) {
        GatewayResponse gatewayResponse = gatewayRouterApi.route(buildGatewayRequest(path, headers, body, request));
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(gatewayResponse.getStatusCode());
        gatewayResponse.getHeaders().forEach(responseBuilder::header);
        if (resolveMethod(request) == HttpMethod.HEAD || gatewayResponse.getBody() == null) {
            return responseBuilder.build();
        }
        return responseBuilder.body(gatewayResponse.getBody());
    }

    private GatewayRequest buildGatewayRequest(String path, HttpHeaders headers, byte[] body, HttpServletRequest request) {
        return GatewayRequest.builder()
                .method(resolveMethod(request))
                .path(normalizePath(path))
                .queryString(request.getQueryString())
                .headers(extractHeaders(headers))
                .body(body == null || body.length == 0 ? null : body)
                .clientIp(extractClientIp(headers, request))
                .protocol(request.getScheme())
                .build();
    }

    private HttpMethod resolveMethod(HttpServletRequest request) {
        return HttpMethod.valueOf(request.getMethod());
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private Map<String, String> extractHeaders(HttpHeaders requestHeaders) {
        Map<String, String> headers = new HashMap<>();
        requestHeaders.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });
        return headers;
    }

    private String extractClientIp(HttpHeaders headers, HttpServletRequest request) {
        String forwardedFor = headers.getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = headers.getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
