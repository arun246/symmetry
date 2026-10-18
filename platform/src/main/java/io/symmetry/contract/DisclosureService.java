package io.symmetry.contract;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Produces the tier-filtered workbench view of a contract.
 *
 * <ul>
 *   <li>{@code L0} — shape and words only: signature, invariants, budget, scoring.</li>
 *   <li>{@code L1} (default) — also sample data.</li>
 *   <li>{@code L2} — also redacted real examples.</li>
 * </ul>
 *
 * <p>The holdout/oracle internals are removed at <em>every</em> tier: the secret
 * exam never leaves the evaluator.
 */
@Component
public class DisclosureService {

    public WorkbenchView workbench(ContractView contract) {
        JsonNode payload = contract.payload();
        ObjectNode filtered = (payload != null && payload.isObject())
                ? ((ObjectNode) payload).deepCopy()
                : JsonNodeFactory.instance.objectNode();

        // Secrets never travel, whatever the tier.
        filtered.remove("holdout");
        JsonNode oracle = filtered.get("oracle");
        if (oracle != null && oracle.isObject()) {
            ((ObjectNode) oracle).remove("holdout");
        }

        String tier = contract.disclosureTier();
        if ("L0".equals(tier)) {
            filtered.remove("samples");
            filtered.remove("realExamples");
        } else if ("L1".equals(tier)) {
            filtered.remove("realExamples");
        }

        return new WorkbenchView(contract.id(), contract.moduleId(), contract.version(),
                contract.language(), contract.taskKind(), contract.workKind(),
                contract.disclosureTier(), contract.taskDocument(), filtered);
    }
}
