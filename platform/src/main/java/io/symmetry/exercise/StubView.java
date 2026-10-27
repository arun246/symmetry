package io.symmetry.exercise;

import java.util.UUID;

public record StubView(
        UUID id,
        String symbolName,
        String symbolKind,
        int startLine,
        int endLine,
        String stubContent,
        String originalHash) {
}
