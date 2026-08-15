package com.adventacademy.apigateway;

import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the aggregated Swagger UI is wired for all three backends: the
 * springdoc.swagger-ui.urls property binds to the three expected named entries
 * (the dropdown switcher), and the UI itself actually renders through the
 * reactive gateway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySwaggerConfigTest {

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", () -> "http://localhost:1");
        registry.add("CONTENT_SERVICE_URL", () -> "http://localhost:1");
        registry.add("MEETING_SERVICE_URL", () -> "http://localhost:1");
        registry.add("MEETING_SERVICE_WS_URL", () -> "ws://localhost:1");
    }

    @Autowired
    private SwaggerUiConfigProperties swaggerUiConfigProperties;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void swaggerUiListsAllThreeServicesByName() {
        List<String> names = swaggerUiConfigProperties.getUrls().stream()
                .map(url -> url.getName())
                .toList();

        assertThat(names).containsExactlyInAnyOrder("User Service", "Content Service", "Meeting Service");
    }

    @Test
    void swaggerUiUrlsPointAtTheGatewayOnlyOpenApiRoutes() {
        var urlsByName = swaggerUiConfigProperties.getUrls().stream()
                .collect(java.util.stream.Collectors.toMap(u -> u.getName(), u -> u.getUrl()));

        assertThat(urlsByName)
                .containsEntry("User Service", "/user-service/v3/api-docs")
                .containsEntry("Content Service", "/content-service/v3/api-docs")
                .containsEntry("Meeting Service", "/meeting-service/v3/api-docs");
    }

    @Test
    void swaggerUiIndexIsServedByTheGateway() {
        webTestClient.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", location -> assertThat(location)
                        .endsWith("/webjars/swagger-ui/index.html"));

        webTestClient.get().uri("/webjars/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }
}
