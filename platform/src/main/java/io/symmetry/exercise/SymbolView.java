package io.symmetry.exercise;

public record SymbolView(
        String name,
        String kind,
        int startLine,
        int endLine,
        int bodyStartLine,
        int bodyEndLine,
        String signature) {
}
