package io.symmetry.identity;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Dev-only identity: when security is disabled, pick a seeded user from the
 * {@code X-Symmetry-User} header (an email) or fall back to the publisher. This
 * keeps {@code @PreAuthorize} meaningful while developing, without pretending to
 * be an identity provider.
 */
@Component
@ConditionalOnProperty(name = "symmetry.security.enabled", havingValue = "false", matchIfMissing = true)
public class DevAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_HEADER = "X-Symmetry-User";
    public static final String DEFAULT_USER = "publisher@symmetry.local";

    private final AppUserRepository users;

    public DevAuthenticationFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = request.getHeader(USER_HEADER);
            if (email == null || email.isBlank()) {
                email = DEFAULT_USER;
            }
            try {
                users.findByEmail(email).filter(AppUser::isActive).ifPresent(user -> {
                    var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user.getEmail(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (RuntimeException ignored) {
                // Database not ready (e.g. during startup probes); continue anonymous.
            }
        }
        chain.doFilter(request, response);
    }
}
