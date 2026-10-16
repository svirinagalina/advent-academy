package com.adventacademy.contentservice.infrastructure.security;

import com.adventacademy.contentservice.application.port.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final com.adventacademy.security.AccessTokenStatusClient accessTokenStatusClient;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, ObjectMapper objectMapper,
            com.adventacademy.security.AccessTokenStatusClient accessTokenStatusClient) {
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
        this.accessTokenStatusClient = accessTokenStatusClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null) {
                JwtProvider.ValidatedToken validatedToken = jwtProvider.validate(token);
                accessTokenStatusClient.requireActive(token);

                var authorities = validatedToken.roles().stream()
                        .map(JwtAuthenticationFilter::normalizeRole)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                var authentication = new UsernamePasswordAuthenticationToken(
                        validatedToken.userId().toString(),
                        null,
                        authorities
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } catch (com.adventacademy.security.AccessTokenStatusClient.InactiveTokenException e) {
            sendUnauthorizedError(response, "Invalid access token");
        } catch (com.adventacademy.security.AccessTokenStatusClient.IdentityUnavailableException e) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Identity service unavailable\",\"status\":503}");
        } catch (JwtException e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            sendUnauthorizedError(response, e.getMessage());
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void sendUnauthorizedError(HttpServletResponse response, String message) throws IOException {
        log.debug("JWT error: {}", message);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Unauthorized");
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static String normalizeRole(String role) {
        if (role.startsWith("ROLE_")) {
            return role;
        }
        return "ROLE_" + role;
    }
}
