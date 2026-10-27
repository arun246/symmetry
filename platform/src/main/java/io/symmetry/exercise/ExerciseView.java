package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

public record ExerciseView(
        UUID id,
        String title,
        String summary,
        String language,
        String status,
        int version,
        String complexity,
        long priceCents,
        String currency,
        int deadlineDays,
        int publicTestCount,
        int stubCount,
        UUID parentExerciseId,
        Instant publishedAt,
        Instant createdAt) {
}
