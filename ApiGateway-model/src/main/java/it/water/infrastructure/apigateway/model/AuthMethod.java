package it.water.infrastructure.apigateway.model;

/**
 * Authentication methods supported by the API Gateway.
 */
public enum AuthMethod {
    JWT_VALIDATION, API_KEY, OAUTH2, BASIC_AUTH, PASSTHROUGH
}
