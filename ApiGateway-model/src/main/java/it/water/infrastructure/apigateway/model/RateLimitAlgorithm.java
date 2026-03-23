package it.water.infrastructure.apigateway.model;

/**
 * Rate limiting algorithms.
 */
public enum RateLimitAlgorithm {
    TOKEN_BUCKET, SLIDING_WINDOW, FIXED_WINDOW
}
