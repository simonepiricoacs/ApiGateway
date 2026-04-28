# The Goal of feature test is to ensure the correct format of json responses
# Functional tests are in GatewaySystemApiTest
Feature: Check Gateway Management Rest Api Response

  Scenario: Health Check Endpoint

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/management/health'
    When method GET
    Then status 200
    And match response ==
    """
    {
      "status": "UP",
      "timestamp": '#number',
      "service": "ApiGateway"
    }
    """

  Scenario: Metrics Endpoint

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/management/metrics'
    When method GET
    Then status 200
    And match response == '#object'

  Scenario: Circuit Breakers Endpoint

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/management/circuit-breakers'
    When method GET
    Then status 200
    And match response == '#object'

  Scenario: Sync Service Discovery Endpoint

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/management/sync'
    When method POST
    Then status 502
    And match response.error contains 'ServiceDiscovery sync failed'
