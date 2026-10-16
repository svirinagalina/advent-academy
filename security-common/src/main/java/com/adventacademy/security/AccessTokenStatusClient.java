package com.adventacademy.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Online revocation check, after local JWT verification. No positive cache or redirects. */
public class AccessTokenStatusClient {
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;

    public AccessTokenStatusClient(String identityUrl) {
        this(identityUrl, Duration.ofSeconds(2));
    }

    public AccessTokenStatusClient(String identityUrl, Duration timeout) {
        this.timeout = timeout;
        this.endpoint = URI.create(identityUrl.replaceAll("/+$", "") + "/api/auth/access-check");
        if (!java.util.Set.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Invalid identity service URL");
        }
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public void requireActive(String accessToken) {
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Authorization", "Bearer " + accessToken).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 401 || status == 403) {
                throw new InactiveTokenException();
            }
            if (status != 204) {
                throw new IdentityUnavailableException();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IdentityUnavailableException();
        } catch (IOException exception) {
            throw new IdentityUnavailableException();
        }
    }

    public static final class InactiveTokenException extends RuntimeException { }
    public static final class IdentityUnavailableException extends RuntimeException { }
}
