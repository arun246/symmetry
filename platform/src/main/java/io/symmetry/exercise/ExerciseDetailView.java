package io.symmetry.exercise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExerciseDetailView(
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
        String taskDocument,
        SourceView source,
        List<StubView> stubs,
        List<ExerciseTestView> tests,
        List<SideCaseView> sideCases,
        UUID parentExerciseId,
        UUID createdBy,
        Instant publishedAt,
        Instant createdAt) {
}
