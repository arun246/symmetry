package io.symmetry.contract;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * Incoming payload when a worker nullifies an acceptance because the task cannot
 * be completed. At least one reason is required by the service; the comment is
 * mandatory when the reason list contains {@code other}.
 */
public record WithdrawRequest(
        @Size(max = 10) List<String> reasons,
        @Size(max = 2000) String comment) {
}
