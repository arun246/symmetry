package io.symmetry.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Enables {@code @PreAuthorize} on controllers and services. */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
