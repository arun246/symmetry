package io.symmetry.exercise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkspaceView(
        ExerciseDetailView exercise,
        UUID sessionId,
        UUID acceptanceId,
        String status,
        Instant deadlineAt,
        boolean executionEnabled) {
}
