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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Map;

/**
 * Route entity - represents a gateway routing rule.
 * Maps incoming requests to upstream services.
 */
@Entity
@Table(name = "gateway_route", uniqueConstraints = @UniqueConstraint(columnNames = {"routeId"}))
@Access(AccessType.FIELD)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter(AccessLevel.PROTECTED)
@ToString
@EqualsAndHashCode(of = {"routeId"}, callSuper = true)
@AccessControl(
        availableActions = {
                CrudActions.SAVE,
                CrudActions.UPDATE,
                CrudActions.FIND,
                CrudActions.FIND_ALL,
                CrudActions.REMOVE,
                ApiGatewayActions.CONFIGURE_RATE_LIMIT,
                ApiGatewayActions.MANAGE_CIRCUIT_BREAKER,
                ApiGatewayActions.VIEW_METRICS,
                ApiGatewayActions.PROXY_REQUEST,
                ApiGatewayActions.REFRESH_ROUTES
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
                                ApiGatewayActions.MANAGE_CIRCUIT_BREAKER,
                                ApiGatewayActions.VIEW_METRICS,
                                ApiGatewayActions.PROXY_REQUEST,
                                ApiGatewayActions.REFRESH_ROUTES
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
                                ApiGatewayActions.VIEW_METRICS,
                                ApiGatewayActions.PROXY_REQUEST,
                                ApiGatewayActions.REFRESH_ROUTES
                        }
                )
        }
)
public class Route extends AbstractJpaEntity implements ProtectedEntity, OwnedResource {

    public static final String DEFAULT_MANAGER_ROLE = "gatewayManager";
    public static final String DEFAULT_VIEWER_ROLE = "gatewayViewer";
    public static final String DEFAULT_OPERATOR_ROLE = "gatewayOperator";

    @NoMalitiusCode
    @NotNull
    @NotNullOnPersist
    @Size(min = 1, max = 100)
    @Column(nullable = false, length = 100)
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private String routeId;

    @NotNull
    @NotNullOnPersist
    @Size(min = 1, max = 500)
    @Column(name = "path_pattern", nullable = false, length = 500)
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private String pathPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", length = 20)
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private HttpMethod method;

    @NoMalitiusCode
    @NotNull
    @NotNullOnPersist
    @Size(min = 1, max = 255)
    @Column(name = "target_service_name", nullable = false, length = 255)
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private String targetServiceName;

    @Size(max = 500)
    @Column(name = "rewrite_path", length = 500)
    @Setter
    @JsonView(WaterJsonView.Extended.class)
    private String rewritePath;

    @Column(name = "priority")
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private int priority;

    @Column(name = "enabled")
    @Setter
    @JsonView(WaterJsonView.Public.class)
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gateway_route_predicates",
            joinColumns = @JoinColumn(name = "route_id"))
    @MapKeyColumn(name = "predicate_key", length = 255)
    @Column(name = "predicate_value", length = 1000)
    @Setter
    @JsonView(WaterJsonView.Extended.class)
    private Map<String, String> predicates;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gateway_route_filters",
            joinColumns = @JoinColumn(name = "route_id"))
    @MapKeyColumn(name = "filter_key", length = 255)
    @Column(name = "filter_value", length = 1000)
    @Setter
    @JsonView(WaterJsonView.Extended.class)
    private Map<String, String> filters;

    @Setter
    @JsonIgnore
    @JsonView({WaterJsonView.Extended.class})
    private Long ownerUserId;

    public Route(String routeId, String pathPattern, HttpMethod method, String targetServiceName, int priority, boolean enabled) {
        this.routeId = routeId;
        this.pathPattern = pathPattern;
        this.method = method;
        this.targetServiceName = targetServiceName;
        this.priority = priority;
        this.enabled = enabled;
    }

    @PrePersist
    protected void onCreate() {
        if (this.method == null) {
            this.method = HttpMethod.ANY;
        }
    }
}
