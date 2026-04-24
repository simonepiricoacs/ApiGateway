package it.water.infrastructure.apigateway.service;

import it.water.infrastructure.apigateway.api.CircuitBreakerApi;
import it.water.infrastructure.apigateway.api.options.GatewaySystemOptions;
import it.water.infrastructure.apigateway.model.CircuitBreakerConfig;
import it.water.infrastructure.apigateway.model.CircuitState;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.api.interceptors.OnActivate;
import it.water.core.api.interceptors.OnDeactivate;
import it.water.core.interceptors.annotations.Inject;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker service implementation with CLOSED/OPEN/HALF_OPEN states.
 */
@FrameworkComponent
public class CircuitBreakerServiceImpl implements CircuitBreakerApi {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerServiceImpl.class);

    private static class CircuitBreakerState {
        final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicLong lastFailureTime = new AtomicLong(0);
        final AtomicLong openedAt = new AtomicLong(0);
    }

    private final Map<String, CircuitBreakerState> states = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerConfig> configs = new ConcurrentHashMap<>();

    @Inject
    @Setter
    private GatewaySystemOptions gatewaySystemOptions;

    @OnActivate
    public void activate() {
        log.info("CircuitBreakerServiceImpl activated");
    }

    @OnDeactivate
    public void deactivate() {
        log.info("CircuitBreakerServiceImpl deactivating: clearing {} states and {} configs",
                states.size(), configs.size());
        states.clear();
        configs.clear();
    }

    @Override
    public boolean allowRequest(String serviceName, String instanceId) {
        String key = buildKey(serviceName, instanceId);
        CircuitBreakerState state = states.computeIfAbsent(key, k -> new CircuitBreakerState());
        CircuitBreakerConfig config = getOrDefaultConfig(serviceName);

        CircuitState current = state.state.get();
        if (current == CircuitState.CLOSED) {
            return true;
        }
        if (current == CircuitState.OPEN) {
            long timeoutMs = config.getTimeoutSeconds() * 1000L;
            if (System.currentTimeMillis() - state.openedAt.get() > timeoutMs) {
                log.debug("Circuit breaker for {}/{} transitioning to HALF_OPEN", serviceName, instanceId);
                state.state.set(CircuitState.HALF_OPEN);
                state.successCount.set(0);
                return true;
            }
            return false;
        }
        // HALF_OPEN: allow one request through
        return true;
    }

    @Override
    public void recordSuccess(String serviceName, String instanceId) {
        String key = buildKey(serviceName, instanceId);
        CircuitBreakerState state = states.computeIfAbsent(key, k -> new CircuitBreakerState());
        CircuitBreakerConfig config = getOrDefaultConfig(serviceName);

        CircuitState current = state.state.get();
        if (current == CircuitState.HALF_OPEN) {
            int successes = state.successCount.incrementAndGet();
            if (successes >= config.getSuccessThreshold()) {
                log.info("Circuit breaker for {}/{} transitioning to CLOSED (recovered)", serviceName, instanceId);
                state.state.set(CircuitState.CLOSED);
                state.failureCount.set(0);
                state.successCount.set(0);
            }
        } else if (current == CircuitState.CLOSED) {
            state.failureCount.set(0);
        }
    }

    @Override
    public void recordFailure(String serviceName, String instanceId) {
        String key = buildKey(serviceName, instanceId);
        CircuitBreakerState state = states.computeIfAbsent(key, k -> new CircuitBreakerState());
        CircuitBreakerConfig config = getOrDefaultConfig(serviceName);

        CircuitState current = state.state.get();
        if (current == CircuitState.HALF_OPEN) {
            log.warn("Circuit breaker for {}/{} transitioning back to OPEN (failure in half-open)", serviceName, instanceId);
            state.state.set(CircuitState.OPEN);
            state.openedAt.set(System.currentTimeMillis());
            state.successCount.set(0);
            return;
        }
        if (current == CircuitState.CLOSED) {
            state.lastFailureTime.set(System.currentTimeMillis());
            int failures = state.failureCount.incrementAndGet();
            if (failures >= config.getFailureThreshold()) {
                log.warn("Circuit breaker for {}/{} transitioning to OPEN (failure threshold reached)", serviceName, instanceId);
                state.state.set(CircuitState.OPEN);
                state.openedAt.set(System.currentTimeMillis());
            }
        }
    }

    @Override
    public CircuitState getState(String serviceName, String instanceId) {
        String key = buildKey(serviceName, instanceId);
        CircuitBreakerState state = states.get(key);
        return state != null ? state.state.get() : CircuitState.CLOSED;
    }

    @Override
    public CircuitBreakerConfig getConfig(String serviceName) {
        return getOrDefaultConfig(serviceName);
    }

    @Override
    public void configure(String serviceName, CircuitBreakerConfig config) {
        log.info("Configuring circuit breaker for service: {}", serviceName);
        configs.put(serviceName, config);
    }

    private String buildKey(String serviceName, String instanceId) {
        return serviceName + ":" + instanceId;
    }

    private CircuitBreakerConfig getOrDefaultConfig(String serviceName) {
        int failureThreshold = gatewaySystemOptions != null
                ? gatewaySystemOptions.getCircuitBreakerFailureThreshold()
                : 5;
        long timeoutMs = gatewaySystemOptions != null
                ? gatewaySystemOptions.getCircuitBreakerTimeoutMs()
                : 30000L;
        return configs.computeIfAbsent(serviceName, k -> CircuitBreakerConfig.builder()
                .serviceName(k)
                .failureThreshold(failureThreshold)
                .successThreshold(3)
                .timeoutSeconds(toTimeoutSeconds(timeoutMs))
                .windowSizeSeconds(60)
                .enabled(true)
                .build());
    }

    private int toTimeoutSeconds(long timeoutMs) {
        if (timeoutMs <= 0L) {
            return 0;
        }
        return (int) Math.max(1L, (timeoutMs + 999L) / 1000L);
    }
}
