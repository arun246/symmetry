package io.symmetry.runner;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobLeaseRepository extends JpaRepository<JobLease, UUID> {

    Optional<JobLease> findByIdAndRunnerId(UUID id, UUID runnerId);

    @Modifying
    @Query("""
            update JobLease l
               set l.status = io.symmetry.runner.LeaseStatus.EXPIRED
             where l.status = io.symmetry.runner.LeaseStatus.ACTIVE
               and l.expiresAt <= :now
            """)
    int expireStale(@Param("now") Instant now);

    @Modifying
    @Query("""
            update JobLease l
               set l.status = io.symmetry.runner.LeaseStatus.REVOKED
             where l.runnerId = :runnerId
               and l.status = io.symmetry.runner.LeaseStatus.ACTIVE
            """)
    int revokeActiveForRunner(@Param("runnerId") UUID runnerId);

    List<JobLease> findByRunnerIdOrderByLeasedAtDesc(UUID runnerId);
}
