# API Gateway

Single entry point for the platform's three backend services — `user-service`,
`content-service`, and `meeting-service` — built on Spring Cloud Gateway.

## Why a reactive gateway

The other services are all Spring MVC (servlet); this module is WebFlux-based instead. That's
deliberate: WebSocket proxying (`/ws/meetings` → `meeting-service`) is a long-proven capability
of the classic reactive Spring Cloud Gateway, and it's a hard requirement here, not something
worth risking on a less mature alternative. The gateway does no business logic — it's pure
I/O proxying — so the reactive/servlet mismatch with the rest of the platform doesn't matter.

## Routes

Every route forwards the exact path each backend already documents in its own OpenAPI spec — no
path rewriting for real traffic, so a request looks identical whether it goes through the
gateway or straight to the service.

| Path(s) | Forwards to |
|---|---|
| `/api/auth/**`, `/api/users/**`, `/api/roles/**`, `/api/permissions/**`, `/api/role-requests/**` | user-service |
| `/api/courses/**`, `/api/directions/**`, `/api/components/**`, `/api/catalog/**`, `/api/lessons/**`, `/api/assets/**` | content-service |
| `/api/meetings/**` | meeting-service (REST) |
| `/ws/meetings` | meeting-service (WebSocket) |

Three more gateway-only paths back the aggregated Swagger UI (see below); they're not real
business routes: `/user-service/v3/api-docs`, `/content-service/v3/api-docs`,
`/meeting-service/v3/api-docs`, each rewritten to the target service's real `/v3/api-docs`.

Route definitions live in one place (`src/main/resources/application.yml`), so the same public
paths are guaranteed for every profile — only the downstream *addresses* change per profile.

## Downstream addresses (environment variables)

| Variable | local default | docker default |
|---|---|---|
| `USER_SERVICE_URL` | `http://localhost:8080` | `http://user-service:8080` |
| `CONTENT_SERVICE_URL` | `http://localhost:8081` | `http://content-service:8081` |
| `MEETING_SERVICE_URL` | `http://localhost:8084` | `http://meeting-service:8084` |
| `MEETING_SERVICE_WS_URL` | `ws://localhost:8084` | `ws://meeting-service:8084` |

The `local`/`docker` profile files only set *defaults* — a real environment variable of the same
name always overrides them. `MEETING_SERVICE_WS_URL` is separate from `MEETING_SERVICE_URL`
because Spring Cloud Gateway picks its WebSocket-vs-HTTP proxying filter from the route's target
URI *scheme*: an `http://` URI on the WebSocket route would silently forward as plain HTTP and
break the upgrade.

## Swagger UI

Open `http://localhost:8090/swagger-ui.html` — the dropdown in the top-right switches between:

- **User Service**
- **Content Service**
- **Meeting Service**

each backed by the corresponding backend's real `/v3/api-docs`, fetched through the gateway
itself (no CORS/cross-origin concerns, since the browser only ever talks to the gateway).

## Running locally

### Whole stack in Docker

```bash
cp -n ../.env.example ../.env
docker compose up -d --build postgres elasticsearch zeebe user-service content-service meeting-service api-gateway
```

### Gateway on the host, backends in Docker

```bash
docker compose up -d postgres elasticsearch zeebe user-service content-service meeting-service
./gradlew :api-gateway:bootRun --args='--spring.profiles.active=local'
```

### Health check

```bash
curl -fsS http://localhost:8090/actuator/health/readiness
```

### Manual smoke test — REST through the gateway

```bash
curl -fsS http://localhost:8090/api/catalog
```

Should return the same response as calling `content-service` directly on port 8081.

### Manual smoke test — WebSocket proxying

Automated coverage stops at asserting the route is *configured* for WebSocket traffic
(`GatewayRouteConfigTest`, target URI scheme `ws://`) — a live socket round-trip through the
gateway needs a running `meeting-service` and is easiest to check by hand. With
[`websocat`](https://github.com/vi/websocat) or any WebSocket client:

```bash
websocat ws://localhost:8090/ws/meetings
```

The connection should establish exactly as it would connecting directly to
`ws://localhost:8084/ws/meetings` — the gateway is a transparent passthrough for this route.

## Tests

- `GatewayRouteConfigTest` — every declared route exists with the right predicate and target
  scheme (read back through `/actuator/gateway/routes`), including that the WebSocket route is
  `ws://`, not `http://`.
- `GatewayRoutingIntegrationIT` — a WireMock stub proves real requests are actually proxied
  (not just configured) for one REST path per service, plus the OpenAPI rewrite route and a
  404 for an unmatched path.
- `GatewaySwaggerConfigTest` — the three named Swagger UI entries are bound correctly and the UI
  itself renders through the gateway.

```bash
./gradlew :api-gateway:test
```

## Note: the gateway actuator endpoint ships disabled by default

`spring-cloud-gateway`'s `/actuator/gateway/routes` endpoint is annotated
`@RestControllerEndpoint(id="gateway", enableByDefault=false)` — exposing it via
`management.endpoints.web.exposure.include` alone is not enough; it also needs
`management.endpoint.gateway.enabled: true`, which is already set in `application.yml`. Worth
knowing if you ever see the endpoint 404 despite exposure looking correct.
