package com.unboxy.gamemanagerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())  // Disable CSRF protection as it's not typically used in API scenarios
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/signup", "/login", "/actuator/beans", "/games", "/games/{id}", "/games/{id}/play", "/games/user/{userId}").permitAll()  // Allow public access to sign up and login endpoints
                .anyExchange().authenticated()  // All other requests must be authenticated
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})  // Use JWT for authentication
            )
            .build();
    }
}
