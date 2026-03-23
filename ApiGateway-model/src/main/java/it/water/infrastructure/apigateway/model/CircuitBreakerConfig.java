package it.water.infrastructure.apigateway.model;

import lombok.*;

/**
 * Configuration for a circuit breaker instance per service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CircuitBreakerConfig {
    private String serviceName;
    @Builder.Default
    private int failureThreshold = 5;
    @Builder.Default
    private int successThreshold = 3;
    @Builder.Default
    private int timeoutSeconds = 30;
    @Builder.Default
    private int windowSizeSeconds = 60;
    @Builder.Default
    private boolean enabled = true;
}
