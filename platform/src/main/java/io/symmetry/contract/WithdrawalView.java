package io.symmetry.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Result of nullifying an acceptance: what was recorded, and why. */
public record WithdrawalView(
        UUID acceptanceId,
        UUID contractId,
        String status,
        List<String> reasons,
        String comment,
        Instant withdrawnAt) {
}
