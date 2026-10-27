package io.symmetry.exercise;

import java.util.List;

import jakarta.validation.Valid;

public record SaveTestsRequest(List<@Valid TestInput> tests, List<@Valid SideCaseInput> sideCases) {
}
