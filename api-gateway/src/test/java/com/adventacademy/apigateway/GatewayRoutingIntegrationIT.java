package com.adventacademy.apigateway;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Proves the gateway actually proxies — not just that routes are configured
 * correctly (see {@link GatewayRouteConfigTest}). A single WireMock instance
 * stands in for all three backends (routing is purely path-based, so which
 * "service" is stubbed doesn't matter for this); one real REST path per
 * service is sent through the gateway and the response/request are checked on
 * both ends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationIT {

    @RegisterExtension
    static WireMockExtension backend = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", backend::baseUrl);
        registry.add("CONTENT_SERVICE_URL", backend::baseUrl);
        registry.add("MEETING_SERVICE_URL", backend::baseUrl);
        registry.add("MEETING_SERVICE_WS_URL", () -> "ws://localhost:1");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void userServiceRequestReachesTheBackendAndResponseComesBackUnchanged() {
        backend.stubFor(get(urlEqualTo("/api/auth/confirm-email"))
                .willReturn(aResponse().withStatus(200).withBody("user-service-response")));

        webTestClient.get().uri("/api/auth/confirm-email")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("user-service-response");

        backend.verify(getRequestedFor(urlEqualTo("/api/auth/confirm-email")));
    }

    @Test
    void contentServiceRequestReachesTheBackendAndResponseComesBackUnchanged() {
        backend.stubFor(get(urlEqualTo("/api/catalog"))
                .willReturn(aResponse().withStatus(200).withBody("content-service-response")));

        webTestClient.get().uri("/api/catalog")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("content-service-response");

        backend.verify(getRequestedFor(urlEqualTo("/api/catalog")));
    }

    @Test
    void meetingServiceRequestReachesTheBackendAndResponseComesBackUnchanged() {
        backend.stubFor(get(urlEqualTo("/api/meetings/rooms/room-1"))
                .willReturn(aResponse().withStatus(200).withBody("meeting-service-response")));

        webTestClient.get().uri("/api/meetings/rooms/room-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("meeting-service-response");

        backend.verify(getRequestedFor(urlEqualTo("/api/meetings/rooms/room-1")));
    }

    @Test
    void openApiRewriteRouteStripsTheServicePrefixBeforeForwarding() {
        backend.stubFor(get(urlEqualTo("/v3/api-docs"))
                .willReturn(aResponse().withStatus(200).withBody("{\"openapi\":\"3.0.1\"}")));

        webTestClient.get().uri("/user-service/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("{\"openapi\":\"3.0.1\"}");

        backend.verify(getRequestedFor(urlEqualTo("/v3/api-docs")));
    }

    @Test
    void unmatchedPathIsNotRoutedAnywhere() {
        webTestClient.get().uri("/api/does-not-exist")
                .exchange()
                .expectStatus().isNotFound();
    }
}
