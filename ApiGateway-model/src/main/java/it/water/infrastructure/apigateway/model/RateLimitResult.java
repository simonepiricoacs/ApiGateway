package it.water.infrastructure.apigateway.model;

import lombok.*;

/**
 * Result of a rate limit check.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitResult {
    private boolean allowed;
    private int remaining;
    private long resetAfterMs;
    private RateLimitRule rule;
}
