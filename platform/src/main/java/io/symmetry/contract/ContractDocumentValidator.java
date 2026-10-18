package io.symmetry.contract;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Checks that a contract's task document and payload are sensible before we store
 * them ({@code docs/IMPLEMENTATION.md} section 2): limits must be positive,
 * invariants must not be empty, and the required sections must be present.
 *
 * <p>Returns every problem it finds so the caller can show a useful message,
 * rather than failing on the first one.
 */
@Component
public class ContractDocumentValidator {

    private static final List<String> BUDGET_FIELDS = List.of("timeMs", "peakMemMb", "artifactKb");

    public List<String> validate(JsonNode payload, String taskDocument) {
        List<String> errors = new ArrayList<>();

        if (taskDocument == null || taskDocument.isBlank()) {
            errors.add("taskDocument must not be blank");
        }
        if (payload == null || !payload.isObject()) {
            errors.add("payload must be a JSON object");
            return errors;
        }

        requireObject(payload, "signature", errors);
        requireNonEmptyArray(payload, "invariants", errors);
        requireObject(payload, "oracle", errors);
        requireObject(payload, "scoring", errors);
        validateBudget(payload.get("resourceBudget"), errors);

        return errors;
    }

    private static void requireObject(JsonNode payload, String field, List<String> errors) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isObject()) {
            errors.add(field + " must be an object");
        }
    }

    private static void requireNonEmptyArray(JsonNode payload, String field, List<String> errors) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            errors.add(field + " must be a non-empty array");
        }
    }

    private static void validateBudget(JsonNode budget, List<String> errors) {
        if (budget == null || !budget.isObject()) {
            errors.add("resourceBudget must be an object");
            return;
        }
        for (String field : BUDGET_FIELDS) {
            JsonNode value = budget.get(field);
            if (value == null || !value.isNumber() || value.asDouble() <= 0) {
                errors.add("resourceBudget." + field + " must be a positive number");
            }
        }
    }
}
