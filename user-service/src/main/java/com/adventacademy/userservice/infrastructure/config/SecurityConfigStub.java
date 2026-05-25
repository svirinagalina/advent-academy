package com.adventacademy.userservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfigStub {

    // TODO: Configure Spring Security properly
    // This is a stub configuration that permits all requests
    //
    // Required configurations:
    // 1. Disable CSRF (stateless JWT auth)
    // 2. Configure CORS
    // 3. Set session management to STATELESS
    // 4. Add JWT authentication filter (before UsernamePasswordAuthenticationFilter)
    // 5. Configure authorization rules:
    //    - Permit: /api/auth/**, /swagger-ui/**, /api-docs/**, /actuator/health
    //    - Require authentication for all other /api/** endpoints
    // 6. Add authentication entry point for 401 errors
    // 7. Disable form login and HTTP basic auth
    //
    // Example structure (to be implemented):
    // @Bean
    // public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    //     http
    //         .csrf(csrf -> csrf.disable())
    //         .cors(cors -> cors.configurationSource(...))
    //         .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    //         .authorizeHttpRequests(auth -> auth
    //             .requestMatchers("/api/auth/**", "/swagger-ui/**", "/api-docs/**").permitAll()
    //             .requestMatchers("/api/**").authenticated()
    //             .anyRequest().permitAll()
    //         )
    //         .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
    //         .exceptionHandling(ex -> ex.authenticationEntryPoint(...));
    //     return http.build();
    // }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/actuator/info",
                                "/api/auth/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
