package it.water.infrastructure.apigateway.api.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import it.water.core.api.service.rest.FrameworkRestApi;
import it.water.core.api.service.rest.RestApi;
import it.water.service.rest.api.security.LoggedIn;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * HTTP entrypoint for proxying requests through the gateway router.
 */
@Path("/proxy")
@Api(tags = "Gateway Proxy API")
@FrameworkRestApi
public interface GatewayProxyRestApi extends RestApi {

    @LoggedIn
    @GET
    @Path("/{path: .+}")
    @ApiOperation(value = "/proxy/{path}", notes = "Proxy GET request", httpMethod = "GET")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation"),
            @ApiResponse(code = 404, message = "No route found"),
            @ApiResponse(code = 502, message = "Proxy failure"),
            @ApiResponse(code = 401, message = "Not authorized")
    })
    Response proxyGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo);

    @LoggedIn
    @POST
    @Path("/{path: .+}")
    @Consumes("*/*")
    @Produces("*/*")
    @ApiOperation(value = "/proxy/{path}", notes = "Proxy POST request", httpMethod = "POST")
    Response proxyPost(@PathParam("path") String path, byte[] body, @Context HttpHeaders headers, @Context UriInfo uriInfo);

    @LoggedIn
    @PUT
    @Path("/{path: .+}")
    @Consumes("*/*")
    @Produces("*/*")
    @ApiOperation(value = "/proxy/{path}", notes = "Proxy PUT request", httpMethod = "PUT")
    Response proxyPut(@PathParam("path") String path, byte[] body, @Context HttpHeaders headers, @Context UriInfo uriInfo);

    @LoggedIn
    @DELETE
    @Path("/{path: .+}")
    @Consumes("*/*")
    @Produces("*/*")
    @ApiOperation(value = "/proxy/{path}", notes = "Proxy DELETE request", httpMethod = "DELETE")
    Response proxyDelete(@PathParam("path") String path, byte[] body, @Context HttpHeaders headers, @Context UriInfo uriInfo);

    @LoggedIn
    @OPTIONS
    @Path("/{path: .+}")
    @Consumes("*/*")
    @Produces("*/*")
    @ApiOperation(value = "/proxy/{path}", notes = "Proxy OPTIONS request", httpMethod = "OPTIONS")
    Response proxyOptions(@PathParam("path") String path, byte[] body, @Context HttpHeaders headers, @Context UriInfo uriInfo);

    @LoggedIn
    @HEAD
    @Path("/{path: .+}")
    @Produces("*/*")
    @ApiOperation(value = "/proxy/{path}", notes = "Proxy HEAD request", httpMethod = "HEAD")
    Response proxyHead(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo);
}
