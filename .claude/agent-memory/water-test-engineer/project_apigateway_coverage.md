---
name: apigateway-coverage-gaps
description: Known coverage gaps in ApiGateway module and strategies to close them, particularly branch coverage bottlenecks
metadata:
  type: project
---

ApiGateway coverage state after 2026-07-03 session: aggregated ~73% instr / 58% branch before improvements.

**Key bottlenecks closed in this session:**

1. `GatewayProxyRestControllerImpl` (JAX-RS) — proxyPost/Put/Delete/Options/Head were 0% (no Karate scenarios for those HTTP verbs). Added 9 scenarios to `gateway-proxy.feature`.

2. `GatewaySystemServiceImpl` — only 31% instruction / 16% branch because `WaterTestExtension` wraps the component in a proxy that cannot be cast to the concrete class. The existing tests in `GatewaySystemApiTest` skipped most assertions via the `resolveImpl()` guard. Fix: added tests ordered 16-28 that instantiate `new GatewaySystemServiceImpl()` directly (bypassing the proxy), covering: `recordRequest`, `getCachedInstances`, `deactivate`, `refreshServiceCache`, `hasRemoteServiceDiscoveryConfigured`, `resolveServiceDiscoveryEndpoint` (all 4 branches: blank URL throws, /water suffix, trailing slash, already-full path), `parseRemoteServiceRegistrations` (null response), `getHealthyInstances` with HALF_OPEN circuit, and `syncWithServiceDiscovery` InterruptedException path.

3. `RateLimiterServiceImpl.TokenBucket` — `remaining()` and `resetAfterMs()` branches. Added tests 12-18 to `RateLimiterApiTest` covering: remaining=0 when exhausted, resetAfterMs>0 when blocked, resetAfterMs=0 when allowed, sliding window blocked result, fixed window blocked result, null-rule removal, empty rules list, no-match fallback.

4. `LoadBalancerServiceImpl` — `reportSuccess`/`reportFailure` when counter map key absent (counter == null branch). Added tests 16-19 to `LoadBalancerApiTest`.

5. `GatewaySystemOptionsImpl` — all 6 methods had uncovered null-applicationProperties branches. Added direct-instantiation tests 13-18 to `GatewaySystemOptionsTest`. Also added test for property with only commas (empty tokens).

6. `GatewayRouterServiceImpl` — 5xx response branch (records failure, not success) and circuit breaker OPEN 503. Added tests 24-26 to `GatewayRouterApiTest`.

**Why:** To reach 80%+ branch coverage SonarQube threshold.

**How to apply:** When adding new service implementations, always test the null-options branches via direct instantiation (not the WaterTestExtension proxy). Use `resolveServiceDiscoveryEndpoint` test pattern for URL normalization tests.
