package com.adventacademy.apigateway;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component("backends")
@ConditionalOnProperty(name = "gateway.readiness.check-backends", havingValue = "true")
public class BackendReadiness implements ReactiveHealthIndicator {
    private final WebClient client;
    private final java.util.List<String> services;
    public BackendReadiness(WebClient.Builder builder, @Value("${USER_SERVICE_URL}") String users,
                            @Value("${CONTENT_SERVICE_URL}") String content, @Value("${MEETING_SERVICE_URL}") String meetings) {
        client=builder.build(); services=java.util.List.of(users,content,meetings);
    }
    @Override
    public Mono<Health> health() {
        return Flux.fromIterable(services).flatMap(url -> client.get().uri(url+"/actuator/health/readiness")
                .retrieve().bodyToMono(java.util.Map.class).map(body -> "UP".equals(body.get("status")))
                .defaultIfEmpty(false).timeout(Duration.ofSeconds(2)).onErrorReturn(false)).all(Boolean::booleanValue)
                .map(ready -> ready ? Health.up().build() : Health.down().build());
    }
}
