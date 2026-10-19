package io.symmetry.eval;

/**
 * A single checkpoint in the gate.
 *
 * <p>The gate is a list of these, not a fixed sequence. V1 plugs in property,
 * fuzz, differential, holdout, and OOD checkers (M4); a simulator, visual
 * comparison, or proof checker can be added later without touching the runner.
 */
public interface Checker {

    /** Stable name of the checkpoint, e.g. {@code "buildOk"} or {@code "holdoutHunter"}. */
    String name();
}
