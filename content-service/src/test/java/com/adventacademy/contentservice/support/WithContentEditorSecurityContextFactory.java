package com.adventacademy.contentservice.support;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the security context behind {@link WithContentEditor}.
 * <p>
 * The principal is the bare user id string, exactly what {@code JwtAuthenticationFilter} stores
 * for a real request, so the production {@code CurrentUserProvider} parses it back into a UUID.
 */
public class WithContentEditorSecurityContextFactory implements WithSecurityContextFactory<WithContentEditor> {

    @Override
    public SecurityContext createSecurityContext(WithContentEditor annotation) {
        final List<SimpleGrantedAuthority> authorities = Arrays.stream(annotation.roles())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        final UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(annotation.userId(), null, authorities);
        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
