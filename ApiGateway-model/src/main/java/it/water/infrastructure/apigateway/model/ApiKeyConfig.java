package it.water.infrastructure.apigateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * Reserved API-key configuration model for the future gateway-authentication
 * layer. It is not used by the current proxy routing path.
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
