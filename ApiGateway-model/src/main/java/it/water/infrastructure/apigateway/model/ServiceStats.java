package it.water.infrastructure.apigateway.model;

import lombok.*;

/**
 * Runtime statistics for a backend service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceStats {
    private String serviceName;
    private long totalRequests;
    private long successCount;
    private long failureCount;
    private double avgLatencyMs;
    private CircuitState circuitState;
}
