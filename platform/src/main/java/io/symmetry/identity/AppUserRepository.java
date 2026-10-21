package io.symmetry.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findBySubject(String subject);

    Page<AppUser> findByOrgId(UUID orgId, Pageable pageable);

    Page<AppUser> findByRole(Role role, Pageable pageable);

    List<AppUser> findByOrgId(UUID orgId);

    long countByRole(Role role);
}
