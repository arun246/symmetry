package io.symmetry.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the caller to a local {@link AppUser} and an {@link Actor}.
 *
 * <p>In deployed mode the JWT {@code sub} identifies the user; the row is
 * provisioned on first sight from the token claims. In dev mode an
 * {@link DevAuthenticationFilter} has already put the dev user's email in the
 * security context.
 */
@Service
public class CurrentUserService {

    private final AppUserRepository users;
    private final OrganizationRepository organizations;

    public CurrentUserService(AppUserRepository users, OrganizationRepository organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    public Actor require() {
        return maybe().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Sign in to continue"));
    }

    public Optional<Actor> maybe() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return Optional.of(actorForJwt(jwt.getToken()));
        }
        if ("anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }
        return users.findByEmail(authentication.getName()).map(this::toActor);
    }

    @Transactional
    public Actor actorForJwt(Jwt jwt) {
        String subject = jwt.getSubject();
        AppUser user = users.findBySubject(subject).orElseGet(() -> provision(jwt));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended");
        }
        return toActor(user);
    }

    private AppUser provision(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getSubject() + "@unknown.local";
        }
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = email;
        }
        Role role = roleFrom(jwt);
        UUID orgId = orgFrom(jwt);
        try {
            return users.save(new AppUser(UUID.randomUUID(), jwt.getSubject(), email, name, role, orgId));
        } catch (DataIntegrityViolationException race) {
            return users.findBySubject(jwt.getSubject())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Could not provision identity"));
        }
    }

    private UUID orgFrom(Jwt jwt) {
        String slug = jwt.getClaimAsString("org_slug");
        if (slug == null || slug.isBlank()) {
            slug = jwt.getClaimAsString("org");
        }
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return organizations.findBySlug(slug).map(Organization::getId).orElse(null);
    }

    private static Role roleFrom(Jwt jwt) {
        Object realm = jwt.getClaim("realm_access");
        if (realm instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            Role found = roleIn(roles);
            if (found != null) {
                return found;
            }
        }
        List<String> direct = jwt.getClaimAsStringList("roles");
        if (direct != null) {
            Role found = roleIn(direct);
            if (found != null) {
                return found;
            }
        }
        return Role.WORKER;
    }

    private static Role roleIn(Collection<?> values) {
        for (Object value : values) {
            try {
                return Role.valueOf(String.valueOf(value).trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Not one of our roles (e.g. an OIDC default role); keep looking.
            }
        }
        return null;
    }

    private Actor toActor(AppUser user) {
        String orgSlug = user.getOrgId() == null ? null
                : organizations.findById(user.getOrgId()).map(Organization::getSlug).orElse(null);
        return new Actor(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getOrgId(), orgSlug);
    }
}
