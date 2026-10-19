package io.symmetry.task;

/**
 * The kind of work a contract describes.
 *
 * <p>V1 only uses {@link #COMPUTE}: a pure, predictable function judged by tests
 * and ranked by speed/memory. The other values exist now so that adding a service,
 * UI, data, or system workload later does not require reworking the job card.
 */
public enum WorkKind {
    COMPUTE,
    SERVICE,
    UI,
    DATA,
    SYSTEM;

    public static WorkKind parse(String value) {
        if (value == null || value.isBlank()) {
            return COMPUTE;
        }
        return WorkKind.valueOf(value.trim().toUpperCase());
    }
}
