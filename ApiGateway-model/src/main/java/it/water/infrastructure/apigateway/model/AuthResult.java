package it.water.infrastructure.apigateway.model;

import lombok.*;

import java.util.List;

/**
 * Result of an authentication check.
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
