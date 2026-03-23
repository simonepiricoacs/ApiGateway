package it.water.infrastructure.apigateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import it.water.core.api.entity.owned.OwnedResource;
import it.water.core.api.permission.ProtectedEntity;
import it.water.core.api.service.rest.WaterJsonView;
import it.water.core.permission.action.CrudActions;
import it.water.core.permission.annotations.AccessControl;
import it.water.core.permission.annotations.DefaultRoleAccess;
import it.water.core.validation.annotations.NoMalitiusCode;
import it.water.core.validation.annotations.NotNullOnPersist;
import it.water.repository.jpa.model.AbstractJpaEntity;
import it.water.infrastructure.apigateway.actions.ApiGatewayActions;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * RateLimitRule entity - defines rate limiting policies for gateway traffic.
 */
@Entity
@Table(name = "gateway_rate_limit_rule", uniqueConstraints = @UniqueConstraint(columnNames = {"ruleId"}))
@Access(AccessType.FIELD)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter(AccessLevel.PROTECTED)
@ToString
@EqualsAndHashCode(of = {"ruleId"}, callSuper = true)
@AccessControl(
        availableActions = {
                CrudActions.SAVE,
                CrudActions.UPDATE,
                CrudActions.FIND,
                CrudActions.FIND_ALL,
                CrudActions.REMOVE,
                ApiGatewayActions.CONFIGURE_RATE_LIMIT,
                ApiGatewayActions.VIEW_METRICS
        },
        rolesPermissions = {
                @DefaultRoleAccess(
                        roleName = Route.DEFAULT_MANAGER_ROLE,
                        actions = {
                                CrudActions.SAVE,
                                CrudActions.UPDATE,
                                CrudActions.FIND,
                                CrudActions.FIND_ALL,
                                CrudActions.REMOVE,
                                ApiGatewayActions.CONFIGURE_RATE_LIMIT,
                                ApiGatewayActions.VIEW_METRICS
                        }
                ),
                @DefaultRoleAccess(
                        roleName = Route.DEFAULT_VIEWER_ROLE,
                        actions = {
                                CrudActions.FIND,
                                CrudActions.FIND_ALL,
                                ApiGatewayActions.VIEW_METRICS
                        }
                ),
                @DefaultRoleAccess(
                        roleName = Route.DEFAULT_OPERATOR_ROLE,
                        actions = {
                                CrudActions.FIND,
                                CrudActions.FIND_ALL,
                                CrudActions.UPDATE,
                                ApiGatewayActions.CONFIGURE_RATE_LIMIT,
                                ApiGatewayActions.VIEW_METRICS
                        }
                )
        }
)
public class RateLimitRule extends AbstractJpaEntity implements ProtectedEntity, OwnedResource {

    @NoMalitiusCode
    @NotNull
    @NotNullOnPersist
    @Size(min = 1, max = 100)
    @Column(nullable = false, length = 100)
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", length = 50)
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private RateLimitKeyType keyType;

    @NoMalitiusCode
    @Size(max = 500)
    @Column(name = "key_pattern", length = 500)
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private String keyPattern;

    @Min(1)
    @Column(name = "max_requests")
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private int maxRequests;

    @Min(1)
    @Column(name = "window_seconds")
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private int windowSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", length = 50)
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private RateLimitAlgorithm algorithm;

    @Column(name = "burst_capacity")
    @Setter
    @JsonView(WaterJsonView.Extended.class)
    private int burstCapacity;

    @Column(name = "enabled")
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private boolean enabled = true;

    @Setter
    @JsonIgnore
    @JsonView({WaterJsonView.Extended.class})
    private Long ownerUserId;

    public RateLimitRule(String ruleId, RateLimitKeyType keyType, int maxRequests, int windowSeconds, RateLimitAlgorithm algorithm) {
        this.ruleId = ruleId;
        this.keyType = keyType;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.algorithm = algorithm;
        this.burstCapacity = maxRequests;
        this.enabled = true;
    }

    @PrePersist
    protected void onCreate() {
        if (this.algorithm == null) {
            this.algorithm = RateLimitAlgorithm.TOKEN_BUCKET;
        }
        if (this.keyType == null) {
            this.keyType = RateLimitKeyType.CLIENT_IP;
        }
        if (this.burstCapacity <= 0) {
            this.burstCapacity = this.maxRequests;
        }
    }
}
