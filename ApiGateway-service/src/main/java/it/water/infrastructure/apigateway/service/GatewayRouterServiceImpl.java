package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.*;
import it.water.infrastructure.apigateway.model.*;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.api.interceptors.OnActivate;
import it.water.service.discovery.model.ServiceRegistration;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Core gateway router service implementation.
 * Routes incoming requests to upstream services via HTTP proxy.
 */
@FrameworkComponent
public class GatewayRouterServiceImpl implements GatewayRouterApi {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouterServiceImpl.class);

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade"
    );

    @Inject
    @Getter
    @Setter
    private LoadBalancerApi loadBalancerApi;

    @Inject
    @Getter
    @Setter
    private CircuitBreakerApi circuitBreakerApi;

    @Inject
    @Getter
    @Setter
    private RateLimiterApi rateLimiterApi;

    @Inject
    @Getter
    @Setter
    private GatewayAuthenticationApi gatewayAuthenticationApi;

    @Inject
    @Getter
    @Setter
    private RequestTransformerApi requestTransformerApi;

    @Inject
    @Getter
    @Setter
    private GatewaySystemApi gatewaySystemApi;

    @Inject
    @Getter
    @Setter
    private RouteRepository routeRepository;

    private volatile List<Route> activeRoutes = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Pattern> routePatternCache = new ConcurrentHashMap<>();
    private HttpClient httpClient;

    @OnActivate
    public void activate() {
        log.info("GatewayRouterServiceImpl activating...");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        refreshRoutes();
        log.info("GatewayRouterServiceImpl activated with {} routes", activeRoutes.size());
    }

    @Override
    public GatewayResponse route(GatewayRequest request) {
        log.debug("Routing request: {} {}", request.getMethod(), request.getPath());

        RouteResult routeResult = resolveRoute(request);
        if (routeResult == null || routeResult.getRoute() == null) {
            return buildErrorResponse(404, "No route found for: " + request.getPath());
        }
        if (routeResult.getSelectedInstance() == null) {
            return buildErrorResponse(503, "No healthy instance available for service: " + routeResult.getRoute().getTargetServiceName());
        }

        Route route = routeResult.getRoute();
        ServiceRegistration instance = routeResult.getSelectedInstance();

        // Rate limit check
        String rateLimitKey = request.getClientIp() != null ? request.getClientIp() : "global";
        RateLimitResult rateLimitResult = rateLimiterApi.checkRateLimit(rateLimitKey, request);
        if (!rateLimitResult.isAllowed()) {
            return buildErrorResponse(429, "Rate limit exceeded. Retry after " + rateLimitResult.getResetAfterMs() + "ms");
        }

        // Circuit breaker check
        if (!circuitBreakerApi.allowRequest(route.getTargetServiceName(), instance.getInstanceId())) {
            return buildErrorResponse(503, "Circuit breaker is OPEN for service: " + route.getTargetServiceName());
        }

        // Transform request
        GatewayRequest transformedRequest = requestTransformerApi.transformRequest(routeResult.getTransformedRequest() != null
                ? routeResult.getTransformedRequest() : request, route);

        // Proxy request
        long startTime = System.currentTimeMillis();
        try {
            GatewayResponse response = proxyRequest(transformedRequest, instance, route);
            long latency = System.currentTimeMillis() - startTime;
            response.setLatencyMs(latency);
            response.setUpstreamInstanceId(instance.getInstanceId());

            if (response.getStatusCode() < 500) {
                circuitBreakerApi.recordSuccess(route.getTargetServiceName(), instance.getInstanceId());
                loadBalancerApi.reportSuccess(route.getTargetServiceName(), instance.getInstanceId(), latency);
            } else {
                circuitBreakerApi.recordFailure(route.getTargetServiceName(), instance.getInstanceId());
                loadBalancerApi.reportFailure(route.getTargetServiceName(), instance.getInstanceId(), new RuntimeException("HTTP " + response.getStatusCode()));
            }

            return requestTransformerApi.transformResponse(response, route);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Proxy request failed for {}: {}", instance.getEndpoint(), e.getMessage());
            circuitBreakerApi.recordFailure(route.getTargetServiceName(), instance.getInstanceId());
            loadBalancerApi.reportFailure(route.getTargetServiceName(), instance.getInstanceId(), e);
            return buildErrorResponse(502, "Bad Gateway: " + e.getMessage());
        }
    }

    @Override
    public RouteResult resolveRoute(GatewayRequest request) {
        List<Route> routes = activeRoutes;
        for (Route route : routes) {
            if (!route.isEnabled()) continue;
            if (matchesRoute(request, route)) {
                // Get healthy instances
                List<ServiceRegistration> instances = gatewaySystemApi.getHealthyInstances(route.getTargetServiceName());
                if (instances.isEmpty()) {
                    // Try cache refresh
                    gatewaySystemApi.syncWithServiceDiscovery();
                    instances = gatewaySystemApi.getHealthyInstances(route.getTargetServiceName());
                }
                ServiceRegistration instance = instances.isEmpty() ? null :
                        loadBalancerApi.selectInstance(route.getTargetServiceName(), request, instances);

                return RouteResult.builder()
                        .route(route)
                        .selectedInstance(instance)
                        .transformedRequest(request)
                        .build();
            }
        }
        return null;
    }

    @Override
    public List<Route> getActiveRoutes() {
        return Collections.unmodifiableList(activeRoutes);
    }

    @Override
    public void refreshRoutes() {
        log.info("Refreshing routes from database");
        try {
            List<Route> newRoutes = routeRepository.findOrderedByPriority();
            List<Route> sortedRoutes = newRoutes.stream()
                    .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                    .toList();
            activeRoutes = new CopyOnWriteArrayList<>(sortedRoutes);
            routePatternCache.clear();
            // Also sync service discovery
            if (gatewaySystemApi != null) {
                gatewaySystemApi.syncWithServiceDiscovery();
            }
            log.info("Loaded {} routes", activeRoutes.size());
        } catch (Exception e) {
            log.warn("Failed to refresh routes: {}", e.getMessage());
        }
    }

    @Override
    public void addDynamicRoute(Route route) {
        log.info("Adding dynamic route: {}", route.getRouteId());
        List<Route> updated = new ArrayList<>(activeRoutes);
        updated.add(route);
        updated.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        activeRoutes = new CopyOnWriteArrayList<>(updated);
        routePatternCache.remove(route.getPathPattern());
    }

    @Override
    public void removeDynamicRoute(String routeId) {
        log.info("Removing dynamic route: {}", routeId);
        List<Route> updated = new ArrayList<>(activeRoutes);
        updated.removeIf(r -> routeId.equals(r.getRouteId()));
        activeRoutes = new CopyOnWriteArrayList<>(updated);
    }

    private boolean matchesRoute(GatewayRequest request, Route route) {
        // Method check
        if (route.getMethod() != null && route.getMethod() != HttpMethod.ANY) {
            if (request.getMethod() != route.getMethod()) return false;
        }
        // Path pattern check
        Pattern pattern = routePatternCache.computeIfAbsent(route.getPathPattern(), this::compilePattern);
        return pattern.matcher(request.getPath()).matches();
    }

    private Pattern compilePattern(String pathPattern) {
        String regex = pathPattern
                .replace("**", ".*")
                .replaceAll("\\{[^}]+}", "[^/]+");
        if (!regex.startsWith("^")) regex = "^" + regex;
        if (!regex.endsWith("$")) regex += "(/.*)?$";
        return Pattern.compile(regex);
    }

    private GatewayResponse proxyRequest(GatewayRequest request, ServiceRegistration instance, Route route) throws IOException, InterruptedException {
        String targetUrl = buildTargetUrl(instance, request);
        log.debug("Proxying to: {}", targetUrl);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30));

        // Copy headers (excluding hop-by-hop)
        request.getHeaders().forEach((name, value) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                try {
                    builder.header(name, value);
                } catch (Exception e) {
                    log.trace("Could not add header {}: {}", name, e.getMessage());
                }
            }
        });

        // Add X-Forwarded headers
        builder.header("X-Forwarded-For", request.getClientIp() != null ? request.getClientIp() : "unknown");
        builder.header("X-Request-ID", request.getRequestId());

        // Set method and body
        byte[] body = request.getBody();
        HttpRequest.BodyPublisher bodyPublisher = (body != null && body.length > 0)
                ? HttpRequest.BodyPublishers.ofByteArray(body)
                : HttpRequest.BodyPublishers.noBody();

        String method = request.getMethod() != null ? request.getMethod().name() : "GET";
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            bodyPublisher = HttpRequest.BodyPublishers.noBody();
        }
        builder.method(method, bodyPublisher);

        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

        Map<String, String> responseHeaders = new HashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) responseHeaders.put(name, values.get(0));
        });

        return GatewayResponse.builder()
                .statusCode(response.statusCode())
                .headers(responseHeaders)
                .body(response.body())
                .build();
    }

    private String buildTargetUrl(ServiceRegistration instance, GatewayRequest request) {
        String endpoint = instance.getEndpoint();
        String path = request.getPath() != null ? request.getPath() : "";
        String query = request.getQueryString() != null && !request.getQueryString().isEmpty()
                ? "?" + request.getQueryString() : "";
        if (endpoint.endsWith("/") && path.startsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + path + query;
    }

    private GatewayResponse buildErrorResponse(int statusCode, String message) {
        byte[] body = message.getBytes();
        return GatewayResponse.builder()
                .statusCode(statusCode)
                .body(body)
                .headers(Map.of("Content-Type", "text/plain"))
                .build();
    }
}
