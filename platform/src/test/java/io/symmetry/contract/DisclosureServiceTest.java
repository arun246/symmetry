package io.symmetry.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DisclosureServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DisclosureService service = new DisclosureService();

    private JsonNode payload() {
        return mapper.readTree("""
                {
                  "signature": {"name": "resize"},
                  "invariants": ["dims_match"],
                  "resourceBudget": {"timeMs": 12, "peakMemMb": 8, "artifactKb": 64},
                  "oracle": {"holdout": "secret", "properties": "ref"},
                  "scoring": {"gate": "all_pass"},
                  "samples": [1, 2],
                  "realExamples": [3],
                  "holdout": "top-secret"
                }
                """);
    }

    private ContractView contract(String tier, JsonNode payload) {
        return new ContractView(UUID.randomUUID(), "module.image.resize", "1.0.0", "optimize",
                "java", "compute", "draft", tier, "task document", payload, Instant.now());
    }

    @Test
    void neverLeaksHoldoutAnywhere() {
        JsonNode original = payload();
        WorkbenchView view = service.workbench(contract("L2", original));

        assertNull(view.payload().get("holdout"));
        assertNull(view.payload().get("oracle").get("holdout"));
        // deep copy: the stored payload must not be mutated
        assertNotNull(original.get("holdout"));
    }

    @Test
    void l0HidesSamplesAndRealExamples() {
        WorkbenchView view = service.workbench(contract("L0", payload()));
        assertNull(view.payload().get("samples"));
        assertNull(view.payload().get("realExamples"));
        assertNotNull(view.payload().get("signature"));
    }

    @Test
    void l1ShowsSamplesButNotRealExamples() {
        WorkbenchView view = service.workbench(contract("L1", payload()));
        assertNotNull(view.payload().get("samples"));
        assertNull(view.payload().get("realExamples"));
    }

    @Test
    void l2ShowsBoth() {
        WorkbenchView view = service.workbench(contract("L2", payload()));
        assertNotNull(view.payload().get("samples"));
        assertNotNull(view.payload().get("realExamples"));
        assertTrue(view.payload().has("invariants"));
    }
}
