package it.water.infrastructure.apigateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Result of a reserved gateway-authentication check.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResult {
    private boolean authenticated;
    private String userId;
    private List<String> roles;
    private String apiKey;
    private AuthMethod method;
}
