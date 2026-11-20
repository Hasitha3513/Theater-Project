package com.cinenest.foundation.internal;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Deployment diagnostics only. Never account admission, migration execution or business readiness. */
@Configuration(proxyBeanMethods = false)
@Profile("cloud-run")
public class CloudRunRuntimeConfiguration {
    public CloudRunRuntimeConfiguration(ConfigurableEnvironment environment) {
        validate(environment);
    }

    public static void validate(ConfigurableEnvironment env) {
        try {
            if (!Set.copyOf(Arrays.asList(env.getActiveProfiles())).equals(Set.of("cloud-run"))) throw new IllegalArgumentException();
            int port = Integer.parseInt(env.getRequiredProperty("PORT"));
            int pool = Integer.parseInt(env.getRequiredProperty("CINENEST_DB_POOL_MAX"));
            if (port < 1024 || port > 65535 || pool < 1 || pool > 32) throw new IllegalArgumentException();
            if (!Set.of("127.0.0.1", "0.0.0.0").contains(env.getProperty("CINENEST_BIND_ADDRESS", "127.0.0.1"))) throw new IllegalArgumentException();
            for (var source : env.getPropertySources()) {
                if (source instanceof EnumerablePropertySource<?> values) {
                    for (String raw : values.getPropertyNames()) {
                        String name = raw.toLowerCase(java.util.Locale.ROOT).replace('_', '.').replace('-', '.');
                        if (name.startsWith("cinenest.") && (name.endsWith(".enabled") || name.endsWith(".execution"))
                                && "true".equalsIgnoreCase(String.valueOf(values.getProperty(raw)))) throw new IllegalArgumentException();
                    }
                }
            }
            if (!env.getProperty("spring.flyway.clean-disabled", Boolean.class, true)
                    || !env.getProperty("spring.flyway.enabled", Boolean.class, true)) throw new IllegalArgumentException();
            String user = env.getRequiredProperty("CINENEST_DB_USER");
            String password = env.getRequiredProperty("CINENEST_DB_PASSWORD");
            if (user.isBlank() || password.isBlank()) throw new IllegalArgumentException();
            String raw = env.getRequiredProperty("CINENEST_DB_URL");
            if (!raw.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            var uri = URI.create(raw.substring(5));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
            var options = Arrays.stream(uri.getRawQuery().split("&"))
                .map(v -> v.split("=", 2)).collect(Collectors.toMap(v -> v[0], v -> v[1]));
            if (!options.keySet().equals(Set.of("sslmode", "sslrootcert")) || !"verify-full".equals(options.get("sslmode"))) throw new IllegalArgumentException();
            Path certificate = Path.of(URLDecoder.decode(options.get("sslrootcert"), StandardCharsets.UTF_8));
            if (!certificate.isAbsolute() || Files.isSymbolicLink(certificate) || !Files.isRegularFile(certificate)
                    || !Files.isReadable(certificate)) throw new IllegalArgumentException();
        } catch (RuntimeException failure) {
            // Never echo environment values or a nested JDBC/URI exception containing secrets.
            throw new IllegalStateException("Cloud Run configuration unavailable or denied");
        }
    }

    @Bean
    FlywayMigrationStrategy validateDeploymentSchema() {
        return flyway -> {
            flyway.validate();
            if (flyway.info().current() == null || flyway.info().pending().length != 0) {
                throw new IllegalStateException("Deployment schema is not current; separate migration approval required");
            }
        };
    }

    @Bean
    @Order(0)
    SecurityFilterChain deploymentHealthBoundary(HttpSecurity http) throws Exception {
        return http.securityMatcher("/health/live", "/health/ready")
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/health/live", "/health/ready").permitAll()
                .anyRequest().denyAll()).build();
    }

    @RestController
    @Profile("cloud-run")
    static class DeploymentHealth {
        private final Flyway flyway;
        DeploymentHealth(Flyway flyway) { this.flyway = flyway; }
        @GetMapping("/health/live") ResponseEntity<Map<String, String>> live() { return result(true); }
        @GetMapping("/health/ready") ResponseEntity<Map<String, String>> ready() {
            try { return result(flyway.info().current() != null && flyway.info().pending().length == 0); }
            catch (RuntimeException unavailable) { return result(false); }
        }
        private ResponseEntity<Map<String, String>> result(boolean available) {
            return ResponseEntity.status(available ? 200 : 503).header("Cache-Control", "no-store")
                .body(Map.of("status", available ? "UP" : "DOWN"));
        }
    }
}
