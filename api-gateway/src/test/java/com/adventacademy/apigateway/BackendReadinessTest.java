package com.adventacademy.apigateway;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.reactive.function.client.WebClient;
import static org.assertj.core.api.Assertions.assertThat;

class BackendReadinessTest {
    @Test void checksRealDependencyResponsesAndRejectsEmptyOrInvalidHealthBodies() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var body=new AtomicReference<>("{\"status\":\"UP\"}");
        server.createContext("/actuator/health/readiness",exchange -> {
            byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            var indicator=new BackendReadiness(WebClient.builder(),url,url,url);
            assertThat(indicator.health().block(Duration.ofSeconds(5)).getStatus()).isEqualTo(Status.UP);
            for(String invalid:java.util.List.of("", "{\"status\":\"DOWN\"}","not JSON")) {
                body.set(invalid);
                assertThat(indicator.health().block(Duration.ofSeconds(5)).getStatus()).isEqualTo(Status.DOWN);
            }
        } finally {server.stop(0);}
    }
}
