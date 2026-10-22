package io.symmetry.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    Page<Contract> findByModuleId(String moduleId, Pageable pageable);

    Page<Contract> findByOrgId(UUID orgId, Pageable pageable);

    Optional<Contract> findByModuleIdAndVersion(String moduleId, String version);

    List<Contract> findByModuleIdOrderByVersionDesc(String moduleId);

    /**
     * Accepted contracts that are not already leased or covered by a live
     * (non-quarantined) result. {@code SKIP LOCKED} lets several runners ask at
     * once without handing the same contract to two of them.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select c from Contract c
             where c.lifecycleState = io.symmetry.task.LifecycleState.ACCEPTED
               and not exists (
                   select l.id from JobLease l
                    where l.contractId = c.id
                      and (l.status = io.symmetry.runner.LeaseStatus.ACTIVE
                           or l.status = io.symmetry.runner.LeaseStatus.COMPLETED))
               and not exists (
                   select r.id from RunnerResult r
                    where r.contractId = c.id
                      and r.quarantined = false)
             order by c.createdAt asc
            """)
    List<Contract> findLeasable(Pageable pageable);
}
