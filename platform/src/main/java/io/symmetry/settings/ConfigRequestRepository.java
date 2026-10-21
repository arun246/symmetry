package io.symmetry.settings;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigRequestRepository extends JpaRepository<ConfigRequest, UUID> {

    Page<ConfigRequest> findByStatus(ConfigRequestStatus status, Pageable pageable);

    Page<ConfigRequest> findByOrgId(UUID orgId, Pageable pageable);

    Page<ConfigRequest> findByOrgIdAndStatus(UUID orgId, ConfigRequestStatus status, Pageable pageable);
}
