# ApiGateway Module — HTTP API Gateway

## Purpose
Provides a production-ready API Gateway for the Water Framework microservices ecosystem. Handles reverse proxying, load balancing, circuit breaking, rate limiting, and service discovery integration. Designed as a standalone Spring Boot service that sits in front of downstream microservices.

## Sub-modules

| Sub-module | Package | Role |
|---|---|---|
| `ApiGateway-api` | `it.water.apigateway.api` | Interfaces: `GatewayApi`, `LoadBalancerApi`, `CircuitBreakerApi`, `RateLimiterApi`, `ProxyHandlerApi`, `ServiceDiscoveryClientApi` |
| `ApiGateway-model` | `it.water.apigateway.model` | Entities: `GatewayRoute`, `GatewayRuleSet`, `RouteAction`, enums, constants |
| `ApiGateway-service` | `it.water.apigateway.service` | Implementations: `GatewayServiceImpl`, `LoadBalancerImpl`, `CircuitBreakerImpl`, `RateLimiterImpl`, `ProxyHandlerImpl`, `ServiceDiscoveryClientImpl` |

## Key Services

### GatewayServiceImpl
- Matches incoming requests to `GatewayRoute` by URL pattern (regex, cached in `ConcurrentHashMap<String, Pattern>`)
- Routes are pre-sorted by priority (descending) and held in a `volatile` unmodifiable list — atomic swap on route updates
- Delegates to `ProxyHandlerImpl` for HTTP forwarding with retry support

### LoadBalancerImpl
- Supports **Round-Robin** and **Weighted Round-Robin** strategies
- Separate atomic counters for each strategy to avoid interference
- Integrates with `ServiceDiscoveryClientApi` to fetch live instances

### CircuitBreakerImpl
- States: `CLOSED` (normal) → `OPEN` (failing) → `HALF_OPEN` (testing)
- Configurable failure threshold and recovery timeout
- Per-route circuit breaker instances

### RateLimiterImpl
- Token bucket algorithm, configurable per route/client
- Thread-safe via `ConcurrentHashMap` of `AtomicLong` counters

### ProxyHandlerImpl
- Forwards HTTP requests using `java.net.http.HttpClient`
- Strips hop-by-hop headers using a `Set<String>` (O(1) lookup): `connection`, `keep-alive`, `transfer-encoding`, `te`, `upgrade`, `proxy-authorization`, `proxy-authenticate`, `trailers`
- Adds `X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Proto` headers

### ServiceDiscoveryClientImpl
- Lazy `HttpClient` initialization via `@OnActivate` using configured timeout
- Queries `ServiceDiscovery` module's REST API to resolve service instances

## GatewayRoute Entity

```java
public class GatewayRoute extends AbstractJpaEntity implements ProtectedEntity, OwnedResource {
    private String routeName;
    private String pathPattern;          // regex pattern matched against request URI
    private String targetServiceName;    // service name in ServiceDiscovery
    private String targetUrl;            // direct URL (if no service discovery)
    private int priority;                // higher = matched first
    private boolean stripPrefix;         // remove matched prefix before forwarding
    private String loadBalancingStrategy; // ROUND_ROBIN, WEIGHTED_ROUND_ROBIN
    private boolean circuitBreakerEnabled;
    private boolean rateLimitEnabled;
    private int rateLimitRequestsPerMinute;
}
```

## REST Endpoints

Base path: `/water/api/gateway` (CXF server adds `/water` prefix automatically)

| Interface | `@Path` annotation | Full URL |
|---|---|---|
| `RouteRestApi` | `/api/gateway/routes` | `/water/api/gateway/routes` |
| `RateLimitRuleRestApi` | `/api/gateway/rate-limits` | `/water/api/gateway/rate-limits` |
| `GatewayManagementRestApi` | `/api/gateway/management` | `/water/api/gateway/management` |

**CRITICAL**: RestApi `@Path` annotations must NOT include the `/water` prefix — the CXF REST server in Water Framework adds `/water` as its base context automatically. Including `/water` in `@Path` would result in a doubled path (`/water/water/...`) and HTTP 404.

## Testing Notes
- **138 tests pass** (unit + Karate REST integration)
- Karate runner: `ApiGatewayRestApiTest` — runs all feature files in `src/test/resources/karate/`
- Feature files: `route-crud.feature`, `rate-limit-rule-crud.feature`, `gateway-management.feature`
- `it.water.application.properties` sets `water.testMode=true` and `water.rest.security.jwt.validate=false` for Karate tests
- Coverage: 79% instructions, 67% branches (REST package: 86%)

## Known Issues
- When multiple Spring contexts are created in the same JVM (e.g., parallel test suites), the static `initialized` flag causes `ComponentRegistry` bean missing errors
- Fix: align all test classes to share the same `@SpringBootTest` configuration

## Configuration Properties
```properties
water.apigateway.proxy.timeout=30000
water.apigateway.circuit.breaker.failure.threshold=5
water.apigateway.circuit.breaker.timeout.ms=60000
water.apigateway.rate.limiter.default.rpm=100
water.apigateway.service.discovery.url=http://localhost:8080
```

## Dependencies
- `it.water.core:Core-api` — component lifecycle, registry
- `it.water.repository:Repository-entity` — `AbstractJpaEntity`, `ProtectedEntity`, `OwnedResource`
- `it.water.jparepository:JpaRepository-spring` — Spring JPA persistence
- `it.water.service.discovery:ServiceDiscovery-api` — service instance resolution
- `it.water.rest:Rest-spring-api` — Spring MVC REST controller base
- `org.springframework.boot:spring-boot-starter-web` — HTTP server
- `java.net.http.HttpClient` (JDK 11+) — reverse proxy HTTP client

## Code Generation Rules
- New route matching strategies: implement `LoadBalancerApi` and register as `@FrameworkComponent`
- New circuit breaker backends: implement `CircuitBreakerApi`
- REST controller (`GatewayRouteRestController`) tested **exclusively via Karate** — no JUnit direct calls
- Route regex patterns are compiled once and cached — always use the cache, never recompile on each request
- Proxy header manipulation uses `Set<String>` for O(1) hop-by-hop header lookup
