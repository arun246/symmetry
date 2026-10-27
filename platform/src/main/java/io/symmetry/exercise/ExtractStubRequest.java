package io.symmetry.exercise;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ExtractStubRequest(@NotBlank String symbolName, @Min(1) int startLine) {
}
