package it.water.infrastructure.apigateway.service.rest.spring;

import it.water.core.api.service.rest.FrameworkRestApi;
import it.water.service.rest.api.security.LoggedIn;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Spring MVC entrypoint for gateway proxy requests.
 */
@RequestMapping("/proxy")
@FrameworkRestApi
public interface GatewayProxySpringRestApi {

    @LoggedIn
    @RequestMapping(value = "/{*path}", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.HEAD})
    ResponseEntity<byte[]> proxy(@PathVariable("path") String path,
                                 @RequestHeader HttpHeaders headers,
                                 @RequestBody(required = false) byte[] body,
                                 HttpServletRequest request);
}
