package io.symmetry.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ContractDocumentValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractDocumentValidator validator = new ContractDocumentValidator();

    private JsonNode json(String value) {
        return mapper.readTree(value);
    }

    @Test
    void acceptsWellFormedDocument() {
        JsonNode payload = json("""
                {
                  "signature": {"name": "resize"},
                  "invariants": ["dims_match"],
                  "resourceBudget": {"timeMs": 12, "peakMemMb": 8, "artifactKb": 64},
                  "oracle": {"holdout": "hidden"},
                  "scoring": {"gate": "all_pass"}
                }
                """);

        assertTrue(validator.validate(payload, "task document").isEmpty());
    }

    @Test
    void rejectsEmptyInvariants() {
        JsonNode payload = json("""
                {
                  "signature": {},
                  "invariants": [],
                  "resourceBudget": {"timeMs": 12, "peakMemMb": 8, "artifactKb": 64},
                  "oracle": {},
                  "scoring": {}
                }
                """);

        List<String> errors = validator.validate(payload, "task document");
        assertTrue(errors.stream().anyMatch(e -> e.contains("invariants")), errors.toString());
    }

    @Test
    void rejectsNonPositiveBudget() {
        JsonNode payload = json("""
                {
                  "signature": {},
                  "invariants": ["a"],
                  "resourceBudget": {"timeMs": 0, "peakMemMb": 8, "artifactKb": 64},
                  "oracle": {},
                  "scoring": {}
                }
                """);

        List<String> errors = validator.validate(payload, "task document");
        assertTrue(errors.stream().anyMatch(e -> e.contains("timeMs")), errors.toString());
    }

    @Test
    void rejectsBlankTaskDocument() {
        JsonNode payload = json("""
                {
                  "signature": {},
                  "invariants": ["a"],
                  "resourceBudget": {"timeMs": 1, "peakMemMb": 1, "artifactKb": 1},
                  "oracle": {},
                  "scoring": {}
                }
                """);

        List<String> errors = validator.validate(payload, "  ");
        assertEquals(1, errors.stream().filter(e -> e.contains("taskDocument")).count(), errors.toString());
    }
}
