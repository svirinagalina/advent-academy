package com.adventacademy.apigateway;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the routes declared in application.yml exist with the expected id,
 * predicate, and target scheme — read back through the actuator gateway-routes
 * endpoint, the same introspection surface ops would use, rather than reflecting
 * on compiled {@code Predicate} internals.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRouteConfigTest {

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", () -> "http://localhost:1");
        registry.add("CONTENT_SERVICE_URL", () -> "http://localhost:1");
        registry.add("MEETING_SERVICE_URL", () -> "http://localhost:1");
        registry.add("MEETING_SERVICE_WS_URL", () -> "ws://localhost:1");
    }

    @Autowired
    private WebTestClient webTestClient;

    private String routesJson() {
        return new String(webTestClient.get().uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBodyContent());
    }

    @Test
    void userServiceRouteCoversItsResourcePaths() {
        String body = routesJson();
        assertRouteExists(body, "user-service-api", "http://localhost:1");
    }

    @Test
    void contentServiceRouteCoversItsResourcePaths() {
        String body = routesJson();
        assertRouteExists(body, "content-service-api", "http://localhost:1");
    }

    @Test
    void meetingServiceRestRouteIsPlainHttp() {
        String body = routesJson();
        assertRouteExists(body, "meeting-service-api", "http://localhost:1");
    }

    @Test
    void meetingServiceWebSocketRouteUsesWsScheme() {
        String body = routesJson();
        // The whole point of this test: an http:// URI here would silently forward as
        // plain HTTP and break the WebSocket upgrade.
        assertRouteExists(body, "meeting-service-ws", "ws://localhost:1");
    }

    @Test
    void openApiRewriteRoutesExistForAllThreeServices() {
        String body = routesJson();
        assertRouteExists(body, "user-service-openapi", "http://localhost:1");
        assertRouteExists(body, "content-service-openapi", "http://localhost:1");
        assertRouteExists(body, "meeting-service-openapi", "http://localhost:1");
    }

    private void assertRouteExists(String body, String routeId, String expectedUri) {
        Assertions.assertThat(body)
                .contains("\"route_id\":\"" + routeId + "\"")
                .contains(expectedUri);
    }
}
