package io.symmetry.eval;

import java.util.Map;

import io.symmetry.scoring.MetricVector;

/**
 * The outcome of running the gate. The message is always the sanitized form a
 * worker may see: it never contains hidden data or incumbent code.
 *
 * @param passed            true only if every configured checker passed
 * @param failedChecker     name of the first failing checker, or {@code null}
 * @param sanitizedMessage  worker-safe explanation
 * @param metrics           measurements gathered so far (flexible named numbers)
 */
public record GateVerdict(
        boolean passed,
        String failedChecker,
        String sanitizedMessage,
        MetricVector metrics) {

    public static GateVerdict pass(Map<String, Double> metrics) {
        return new GateVerdict(true, null, "all checkpoints passed", MetricVector.of(metrics));
    }

    public static GateVerdict fail(String failedChecker, String sanitizedMessage) {
        return new GateVerdict(false, failedChecker, sanitizedMessage, MetricVector.empty());
    }
}
