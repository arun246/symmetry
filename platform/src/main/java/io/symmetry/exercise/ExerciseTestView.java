package io.symmetry.exercise;

import java.util.UUID;

public record ExerciseTestView(
        UUID id,
        UUID stubId,
        String name,
        String visibility,
        String content,
        int timeoutMs) {
}
