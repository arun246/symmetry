package io.symmetry.exercise;

import java.util.List;

public record ImportedSource(ExerciseSource source, List<String> findings) {
}
