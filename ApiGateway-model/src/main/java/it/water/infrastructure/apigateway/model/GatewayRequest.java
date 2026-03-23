package it.water.infrastructure.apigateway.model;

import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an incoming request to the API Gateway.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayRequest {
    @Builder.Default
    private String requestId = UUID.randomUUID().toString();
    private HttpMethod method;
    private String path;
    private String queryString;
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();
    private byte[] body;
    private String clientIp;
    private String protocol;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
}
