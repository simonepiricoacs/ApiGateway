/*
 * Copyright 2024 Aristide Cittadino
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.water.infrastructure.apigateway.model;

/**
 * Property keys used by the ApiGateway module.
 * Values are read via ApplicationProperties through the Options pattern
 * (see GatewaySystemOptions).
 */
public abstract class GatewayConstants {

    /**
     * URL of the ServiceDiscovery REST endpoint used by the gateway to sync
     * the list of available services when the local ServiceRegistrationApi is
     * not available (e.g., gateway running in a separate runtime from the
     * ServiceDiscovery).
     * Example: http://service-discovery:8181/water
     */
    public static final String PROP_SERVICE_DISCOVERY_URL = "water.apigateway.service.discovery.url";
    public static final String PROP_PROXY_TIMEOUT_MS = "water.apigateway.proxy.timeout";
    public static final String PROP_CIRCUIT_BREAKER_FAILURE_THRESHOLD =
            "water.apigateway.circuit.breaker.failure.threshold";
    public static final String PROP_CIRCUIT_BREAKER_TIMEOUT_MS =
            "water.apigateway.circuit.breaker.timeout.ms";
    public static final String PROP_RATE_LIMITER_DEFAULT_RPM =
            "water.apigateway.rate.limiter.default.rpm";

    private GatewayConstants() {
        // prevent instantiation
    }
}
