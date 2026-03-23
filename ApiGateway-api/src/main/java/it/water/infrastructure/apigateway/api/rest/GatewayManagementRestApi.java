package it.water.infrastructure.apigateway.api.rest;

import it.water.infrastructure.apigateway.model.ServiceStats;
import it.water.core.api.service.rest.FrameworkRestApi;
import it.water.core.api.service.rest.RestApi;
import it.water.service.rest.api.security.LoggedIn;
import io.swagger.annotations.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * REST API interface for Gateway management operations.
 */
@Path("/api/gateway/management")
@Api(produces = MediaType.APPLICATION_JSON, tags = "Gateway Management API")
@FrameworkRestApi
public interface GatewayManagementRestApi extends RestApi {

    @LoggedIn
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "/health", notes = "Gateway Health Check", httpMethod = "GET")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Healthy"),
            @ApiResponse(code = 401, message = "Not authorized")
    })
    Map<String, Object> health();

    @LoggedIn
    @GET
    @Path("/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "/metrics", notes = "Service Metrics", httpMethod = "GET")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Metrics retrieved"),
            @ApiResponse(code = 401, message = "Not authorized")
    })
    Map<String, ServiceStats> metrics();

    @LoggedIn
    @GET
    @Path("/circuit-breakers")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "/circuit-breakers", notes = "Circuit Breaker States", httpMethod = "GET")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "States retrieved"),
            @ApiResponse(code = 401, message = "Not authorized")
    })
    Map<String, String> circuitBreakers();

    @LoggedIn
    @POST
    @Path("/sync")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "/sync", notes = "Sync with ServiceDiscovery", httpMethod = "POST")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Synced"),
            @ApiResponse(code = 401, message = "Not authorized")
    })
    void syncServiceDiscovery();
}
