package io.symmetry.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    Page<Contract> findByModuleId(String moduleId, Pageable pageable);

    Page<Contract> findByOrgId(UUID orgId, Pageable pageable);

    Optional<Contract> findByModuleIdAndVersion(String moduleId, String version);

    List<Contract> findByModuleIdOrderByVersionDesc(String moduleId);
}
