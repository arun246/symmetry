package io.symmetry.exercise;

import java.util.List;

public record StaticCheckResult(boolean passed, List<String> findings) {
}
