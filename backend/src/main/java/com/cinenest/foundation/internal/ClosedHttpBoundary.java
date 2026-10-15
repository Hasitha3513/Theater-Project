package com.cinenest.foundation.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class ClosedHttpBoundary {
    @Bean
    SecurityFilterChain closedBoundary(HttpSecurity http) throws Exception {
        // No protected feature exists yet. No permissive development profile or login bypass.
        return http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll()).build();
    }
}
