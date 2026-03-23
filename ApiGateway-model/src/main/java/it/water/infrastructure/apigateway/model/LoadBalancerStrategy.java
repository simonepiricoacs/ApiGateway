package it.water.infrastructure.apigateway.model;

/**
 * Load balancer selection strategies.
 */
public enum LoadBalancerStrategy {
    ROUND_ROBIN, WEIGHTED_ROUND_ROBIN, LEAST_CONNECTIONS, RANDOM, IP_HASH
}
