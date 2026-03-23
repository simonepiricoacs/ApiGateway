package it.water.infrastructure.apigateway.model;

import lombok.*;

import java.util.Date;
import java.util.List;

/**
 * Configuration for an API key.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyConfig {
    private String apiKey;
    private String description;
    private List<String> allowedPaths;
    private int rateLimit;
    @Builder.Default
    private boolean enabled = true;
    private Date expiresAt;
}
