# The goal of this feature test is to exercise the real /proxy/** REST boundary.
# Functional routing logic stays covered in GatewayRouterApiTest.
Feature: Check Gateway Proxy Rest Api Response

  Scenario: Proxy returns 404 when no route matches

    Given header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/no-route'
    When method GET
    Then status 404
    And match response == 'No route found for: /karate/no-route'

  Scenario: Proxy returns 503 when route exists but no healthy instance is available

    * def routeId = 'karate-proxy-route-' + randomSeed

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes'
    And request
    """
    {
      "entityVersion": 1,
      "routeId": "#(routeId)",
      "pathPattern": "/karate/proxy/**",
      "method": "GET",
      "targetServiceName": "karate-proxy-backend",
      "priority": 50,
      "enabled": true
    }
    """
    When method POST
    Then status 200
    * def routeEntityId = response.id

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes/refresh'
    When method POST
    Then status 204

    Given header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/proxy/test'
    When method GET
    Then status 503
    And match response == 'No healthy instance available for service: karate-proxy-backend'

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes/' + routeEntityId
    When method DELETE
    Then status 204

  Scenario: Proxy POST returns 404 when no route matches

    Given header Content-Type = 'application/json'
    And header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/no-route-post'
    And request { "data": "test" }
    When method POST
    Then status 404

  Scenario: Proxy PUT returns 404 when no route matches

    Given header Content-Type = 'application/json'
    And header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/no-route-put'
    And request { "data": "update" }
    When method PUT
    Then status 404

  Scenario: Proxy DELETE returns 404 when no route matches

    Given header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/no-route-delete'
    When method DELETE
    Then status 404

  Scenario: Proxy OPTIONS returns 404 when no route matches

    Given header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/no-route-options'
    When method OPTIONS
    Then status 404

  Scenario: Proxy HEAD returns 404 when no route matches (no body expected)

    Given header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/no-route-head'
    When method HEAD
    Then status 404

  Scenario: Proxy POST returns 503 when route exists but no healthy instance available

    * def routeIdPost = 'karate-proxy-post-' + randomSeed

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes'
    And request
    """
    {
      "entityVersion": 1,
      "routeId": "#(routeIdPost)",
      "pathPattern": "/karate/proxy-post/**",
      "method": "POST",
      "targetServiceName": "karate-proxy-post-backend",
      "priority": 45,
      "enabled": true
    }
    """
    When method POST
    Then status 200
    * def routeEntityIdPost = response.id

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes/refresh'
    When method POST
    Then status 204

    Given header Content-Type = 'application/json'
    And header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/proxy-post/item'
    And request { "data": "payload" }
    When method POST
    Then status 503

    Given url serviceBaseUrl + '/water/api/gateway/routes/' + routeEntityIdPost
    When method DELETE
    Then status 204

  Scenario: Proxy PUT returns 503 when route exists but no healthy instance available

    * def routeIdPut = 'karate-proxy-put-' + randomSeed

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes'
    And request
    """
    {
      "entityVersion": 1,
      "routeId": "#(routeIdPut)",
      "pathPattern": "/karate/proxy-put/**",
      "method": "ANY",
      "targetServiceName": "karate-proxy-put-backend",
      "priority": 44,
      "enabled": true
    }
    """
    When method POST
    Then status 200
    * def routeEntityIdPut = response.id

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes/refresh'
    When method POST
    Then status 204

    Given header Content-Type = 'application/json'
    And header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/proxy-put/item/1'
    And request { "data": "update" }
    When method PUT
    Then status 503

    Given url serviceBaseUrl + '/water/api/gateway/routes/' + routeEntityIdPut
    When method DELETE
    Then status 204

  Scenario: Proxy DELETE returns 503 when route exists but no healthy instance available

    * def routeIdDel = 'karate-proxy-del-' + randomSeed

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes'
    And request
    """
    {
      "entityVersion": 1,
      "routeId": "#(routeIdDel)",
      "pathPattern": "/karate/proxy-del/**",
      "method": "ANY",
      "targetServiceName": "karate-proxy-del-backend",
      "priority": 43,
      "enabled": true
    }
    """
    When method POST
    Then status 200
    * def routeEntityIdDel = response.id

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes/refresh'
    When method POST
    Then status 204

    Given header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/proxy-del/item/1'
    When method DELETE
    Then status 503

    Given url serviceBaseUrl + '/water/api/gateway/routes/' + routeEntityIdDel
    When method DELETE
    Then status 204

  Scenario: Proxy OPTIONS returns 503 when route exists but no healthy instance available

    * def routeIdOpt = 'karate-proxy-opt-' + randomSeed

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes'
    And request
    """
    {
      "entityVersion": 1,
      "routeId": "#(routeIdOpt)",
      "pathPattern": "/karate/proxy-opt/**",
      "method": "ANY",
      "targetServiceName": "karate-proxy-opt-backend",
      "priority": 42,
      "enabled": true
    }
    """
    When method POST
    Then status 200
    * def routeEntityIdOpt = response.id

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes/refresh'
    When method POST
    Then status 204

    Given header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/proxy-opt/resource'
    When method OPTIONS
    Then status 503

    Given url serviceBaseUrl + '/water/api/gateway/routes/' + routeEntityIdOpt
    When method DELETE
    Then status 204

  Scenario: Proxy HEAD returns 503 when route exists but no healthy instance available (no body)

    * def routeIdHead = 'karate-proxy-head-' + randomSeed

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes'
    And request
    """
    {
      "entityVersion": 1,
      "routeId": "#(routeIdHead)",
      "pathPattern": "/karate/proxy-head/**",
      "method": "ANY",
      "targetServiceName": "karate-proxy-head-backend",
      "priority": 41,
      "enabled": true
    }
    """
    When method POST
    Then status 200
    * def routeEntityIdHead = response.id

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl + '/water/api/gateway/routes/refresh'
    When method POST
    Then status 204

    Given header Accept = 'text/plain'
    Given url serviceBaseUrl + '/water/proxy/karate/proxy-head/resource'
    When method HEAD
    Then status 503

    Given url serviceBaseUrl + '/water/api/gateway/routes/' + routeEntityIdHead
    When method DELETE
    Then status 204
