package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.RequestTransformerApi;
import it.water.infrastructure.apigateway.model.*;
import it.water.core.interceptors.annotations.FrameworkComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Request/Response transformer service implementation.
 * Handles header manipulation, path rewriting, and filter application.
 */
@FrameworkComponent
public class RequestTransformerServiceImpl implements RequestTransformerApi {

    private static final Logger log = LoggerFactory.getLogger(RequestTransformerServiceImpl.class);

    @Override
    public GatewayRequest transformRequest(GatewayRequest request, Route route) {
        if (request == null || route == null) {
            return request;
        }
        log.debug("Transforming request for route: {}", route.getRouteId());
        GatewayRequest transformed = GatewayRequest.builder()
                .requestId(request.getRequestId())
                .method(request.getMethod())
                .path(request.getPath())
                .queryString(request.getQueryString())
                .headers(new HashMap<>(request.getHeaders()))
                .body(request.getBody())
                .clientIp(request.getClientIp())
                .protocol(request.getProtocol())
                .timestamp(request.getTimestamp())
                .attributes(new HashMap<>(request.getAttributes()))
                .build();

        Map<String, String> filters = route.getFilters();
        if (filters != null) {
            applyHeaderFilters(transformed, filters);
            applyQueryParamFilters(transformed, filters);
        }

        // Strip prefix
        if (route.getRewritePath() != null && !route.getRewritePath().isEmpty()) {
            transformed.setPath(rewritePath(request.getPath(), route.getPathPattern(), route.getRewritePath()));
        }

        return transformed;
    }

    @Override
    public GatewayResponse transformResponse(GatewayResponse response, Route route) {
        if (response == null || route == null) {
            return response;
        }
        log.debug("Transforming response for route: {}", route.getRouteId());
        Map<String, String> filters = route.getFilters();
        if (filters == null) return response;

        GatewayResponse transformed = GatewayResponse.builder()
                .statusCode(response.getStatusCode())
                .headers(new HashMap<>(response.getHeaders()))
                .body(response.getBody())
                .latencyMs(response.getLatencyMs())
                .upstreamInstanceId(response.getUpstreamInstanceId())
                .metadata(new HashMap<>(response.getMetadata()))
                .build();

        // Remove response headers
        String removeHeader = filters.get("removeResponseHeader");
        if (removeHeader != null) {
            for (String h : removeHeader.split(",")) {
                transformed.getHeaders().remove(h.trim());
            }
        }
        // Add response headers
        filters.entrySet().stream()
                .filter(e -> e.getKey().startsWith("addResponseHeader."))
                .forEach(e -> transformed.getHeaders().put(e.getKey().substring("addResponseHeader.".length()), e.getValue()));

        return transformed;
    }

    private void applyHeaderFilters(GatewayRequest request, Map<String, String> filters) {
        // Add headers: addHeader.<name>=<value>
        filters.entrySet().stream()
                .filter(e -> e.getKey().startsWith("addHeader."))
                .forEach(e -> request.getHeaders().put(e.getKey().substring("addHeader.".length()), e.getValue()));

        // Remove headers: removeHeader=<name1>,<name2>
        String removeHeader = filters.get("removeHeader");
        if (removeHeader != null) {
            for (String h : removeHeader.split(",")) {
                request.getHeaders().remove(h.trim());
            }
        }

        // Modify headers: modifyHeader.<name>=<value>
        filters.entrySet().stream()
                .filter(e -> e.getKey().startsWith("modifyHeader."))
                .forEach(e -> request.getHeaders().put(e.getKey().substring("modifyHeader.".length()), e.getValue()));
    }

    private void applyQueryParamFilters(GatewayRequest request, Map<String, String> filters) {
        filters.entrySet().stream()
                .filter(e -> e.getKey().startsWith("addParam."))
                .forEach(e -> {
                    String paramName = e.getKey().substring("addParam.".length());
                    String current = request.getQueryString() != null ? request.getQueryString() : "";
                    if (!current.isEmpty()) current += "&";
                    request.setQueryString(current + paramName + "=" + e.getValue());
                });
    }

    private String rewritePath(String originalPath, String pathPattern, String rewritePath) {
        try {
            // Convert path pattern to regex (replace {var} with capture groups)
            String regexPattern = pathPattern.replace("**", "(.*)").replaceAll("\\{[^}]+}", "([^/]+)");
            Pattern p = Pattern.compile(regexPattern);
            Matcher m = p.matcher(originalPath);
            if (m.matches()) {
                String result = rewritePath;
                for (int i = 1; i <= m.groupCount(); i++) {
                    result = result.replace("$" + i, m.group(i) != null ? m.group(i) : "");
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Path rewrite failed for pattern {} -> {}: {}", pathPattern, rewritePath, e.getMessage());
        }
        return originalPath;
    }
}
