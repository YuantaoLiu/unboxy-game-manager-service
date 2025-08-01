package com.unboxy.gamemanagerservice.filter;

import com.unboxy.gamemanagerservice.utils.UserUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class ReactiveContextFilter implements WebFilter {
    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange,@NonNull WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    Authentication authentication = securityContext.getAuthentication();
                    if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                        Jwt jwt = (Jwt) authentication.getPrincipal();
                        // Extract user ID from the JWT token
                        String userId = jwt.getClaimAsString("sub"); // Adjust as needed
                        // Propagate userId in the Reactive Context
                        return Mono.just(Context.of(UserUtils.USER_ID, userId));
                    }
                    return Mono.empty(); // No context to add
                })
                .defaultIfEmpty(Context.empty()) // Default context if no security context is found
                .flatMap(context -> chain.filter(exchange).contextWrite(context));
    }
}
