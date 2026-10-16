package com.cinenest.foundation.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cinenest.identity.enabled", havingValue = "false", matchIfMissing = true)
class ClosedHttpBoundary {
    @Bean
    SecurityFilterChain closedBoundary(HttpSecurity http) throws Exception {
        // No protected feature exists yet. No permissive development profile or login bypass.
        return http.authorizeHttpRequests(requests -> requests.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/catalog/venues", "/api/catalog/rooms", "/api/catalog/rooms/*").permitAll().anyRequest().denyAll()).build();
    }
}
