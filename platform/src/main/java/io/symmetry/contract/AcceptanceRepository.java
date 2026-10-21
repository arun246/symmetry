package io.symmetry.contract;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcceptanceRepository extends JpaRepository<Acceptance, UUID> {

    List<Acceptance> findByContractId(UUID contractId);

    List<Acceptance> findByWorkerIdOrderByAcceptedAtDesc(String workerId);
}
