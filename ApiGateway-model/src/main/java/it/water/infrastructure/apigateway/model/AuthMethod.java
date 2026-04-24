package it.water.infrastructure.apigateway.model;

/**
 * Authentication methods reserved for the future gateway-authentication layer.
 */
public enum AuthMethod {
    JWT_VALIDATION,
    API_KEY,
    OAUTH2,
    BASIC_AUTH,
    PASSTHROUGH
}
