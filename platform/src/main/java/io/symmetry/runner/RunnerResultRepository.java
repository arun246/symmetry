package io.symmetry.runner;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RunnerResultRepository extends JpaRepository<RunnerResult, UUID> {

    Optional<RunnerResult> findByLeaseId(UUID leaseId);

    boolean existsByContractIdAndQuarantinedFalse(UUID contractId);

    @Modifying
    @Query("""
            update RunnerResult r
               set r.quarantined = true
             where r.runnerId = :runnerId
            """)
    int quarantineForRunner(@Param("runnerId") UUID runnerId);
}
