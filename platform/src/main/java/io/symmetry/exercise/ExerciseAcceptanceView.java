package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

public record ExerciseAcceptanceView(
        UUID id,
        UUID exerciseId,
        String workerId,
        String taskDocumentHash,
        boolean verified,
        String status,
        Instant acceptedAt,
        Instant deadlineAt) {
}
