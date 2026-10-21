package io.symmetry.identity;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a publisher, a worker, an admin and one organization when security is
 * off, so every role can be exercised locally and in tests. Never runs when
 * {@code symmetry.security.enabled=true}: deployed identities come from Keycloak.
 */
@Component
@ConditionalOnProperty(name = "symmetry.security.enabled", havingValue = "false", matchIfMissing = true)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final OrganizationRepository organizations;
    private final AppUserRepository users;

    public DevDataSeeder(OrganizationRepository organizations, AppUserRepository users) {
        this.organizations = organizations;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Organization acme = organizations.findBySlug("acme")
                .orElseGet(() -> organizations.save(
                        new Organization(UUID.randomUUID(), "acme", "Acme Engineering")));

        seed("dev-worker", "worker@symmetry.local", "Wren Worker", Role.WORKER, acme.getId());
        seed("dev-publisher", "publisher@symmetry.local", "Pat Publisher", Role.PUBLISHER, acme.getId());
        seed("dev-admin", "admin@symmetry.local", "Avery Admin", Role.ADMIN, null);
        log.info("Dev identities ready: worker/publisher/admin @symmetry.local (org: acme)");
    }

    private void seed(String subject, String email, String name, Role role, UUID orgId) {
        if (users.findBySubject(subject).isEmpty() && users.findByEmail(email).isEmpty()) {
            users.save(new AppUser(UUID.randomUUID(), subject, email, name, role, orgId));
        }
    }
}
