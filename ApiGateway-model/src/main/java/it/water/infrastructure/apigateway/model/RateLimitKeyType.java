package it.water.infrastructure.apigateway.model;

/**
 * Key types for rate limiting.
 */
public enum RateLimitKeyType {
    CLIENT_IP, USER_ID, API_KEY, SERVICE, GLOBAL
}
