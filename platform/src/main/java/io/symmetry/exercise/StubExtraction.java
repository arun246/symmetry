package io.symmetry.exercise;

public record StubExtraction(
        String symbolName,
        String symbolKind,
        int startLine,
        int endLine,
        String stubContent,
        String originalHash) {
}
