package io.symmetry.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import io.symmetry.identity.DevAuthenticationFilter;

/**
 * Security wiring.
 *
 * <p>For local development and tests, security is <em>off</em> (every request is
 * allowed) so the walking skeleton runs without an identity server. Set
 * {@code symmetry.security.enabled=true} and an OAuth2 issuer to require a JWT,
 * which is how Keycloak is used in deployed environments.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Keep the dev filter out of the container-wide chain; it belongs inside the
     * Spring Security chain so it runs after the security context is established.
     */
    @Bean
    @ConditionalOnProperty(name = "symmetry.security.enabled", havingValue = "false", matchIfMissing = true)
    FilterRegistrationBean<DevAuthenticationFilter> devAuthenticationFilterRegistration(
            DevAuthenticationFilter filter) {
        FilterRegistrationBean<DevAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "symmetry.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain openFilterChain(HttpSecurity http,
            DevAuthenticationFilter devAuthenticationFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .addFilterBefore(devAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "symmetry.security.enabled", havingValue = "true")
    SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Runner machines authenticate with their issued bearer token,
                        // not a user JWT. Admin-only runner reads and revocation still
                        // require a normal session below.
                        .requestMatchers(HttpMethod.POST, "/runners/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/runners/*/heartbeat",
                                "/runners/*/lease", "/runners/*/results").permitAll()
                        // The task list is the public index of the app.
                        .requestMatchers(HttpMethod.GET, "/exercises", "/exercises/*").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(keycloakRealmRoles())));
        return http.build();
    }

    /**
     * Keycloak puts roles under {@code realm_access.roles}; Spring expects flat
     * claims and a {@code ROLE_} prefix. Without this, every {@code hasRole}
     * check would fail in a real deployment.
     */
    private static Converter<Jwt, AbstractAuthenticationToken> keycloakRealmRoles() {
        return jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Object realm = jwt.getClaim("realm_access");
            if (realm instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
                for (Object role : roles) {
                    authorities.add(new SimpleGrantedAuthority(
                            "ROLE_" + String.valueOf(role).trim().toUpperCase()));
                }
            }
            String name = jwt.getClaimAsString("email");
            return new JwtAuthenticationToken(jwt, authorities, name);
        };
    }
}
