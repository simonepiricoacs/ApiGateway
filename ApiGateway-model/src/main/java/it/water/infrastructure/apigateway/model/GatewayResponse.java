package it.water.infrastructure.apigateway.model;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the response from the API Gateway proxy operation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayResponse {
    private int statusCode;
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();
    private byte[] body;
    private long latencyMs;
    private String upstreamInstanceId;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
