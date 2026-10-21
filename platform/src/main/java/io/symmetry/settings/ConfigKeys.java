package io.symmetry.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;

/**
 * The allow-list of typed configuration keys. Unknown keys are rejected so a
 * typo cannot silently create an inert setting that looks applied.
 */
public final class ConfigKeys {

    public static final String WORKER_ACCEPTANCE_REQUIRED = "worker.acceptanceRequired";
    public static final String WORKER_MAX_CONCURRENT = "worker.maxConcurrent";
    public static final String EVALUATION_TIME_LIMIT_SECONDS = "evaluation.timeLimitSeconds";
    public static final String EVALUATION_FUEL_LIMIT = "evaluation.fuelLimit";
    public static final String SCORING_PARETO_WEIGHTS = "scoring.paretoWeights";
    public static final String NOTIFICATIONS_EMAIL = "notifications.email";
    public static final String RETENTION_SUBMISSION_DAYS = "retention.submissionDays";
    public static final String AI_ASSIST_ENABLED = "ai.assistEnabled";
    public static final String CONTRACT_AUTO_PUBLISH = "contract.autoPublish";

    private static final Set<String> ORG_KEYS = Set.of(
            WORKER_ACCEPTANCE_REQUIRED,
            EVALUATION_TIME_LIMIT_SECONDS,
            EVALUATION_FUEL_LIMIT,
            SCORING_PARETO_WEIGHTS,
            RETENTION_SUBMISSION_DAYS,
            CONTRACT_AUTO_PUBLISH);

    private static final Set<String> USER_KEYS = Set.of(
            NOTIFICATIONS_EMAIL,
            AI_ASSIST_ENABLED,
            WORKER_MAX_CONCURRENT);

    private static final Map<String, Consumer<JsonNode>> VALIDATORS;

    static {
        Map<String, Consumer<JsonNode>> validators = new LinkedHashMap<>();
        validators.put(WORKER_ACCEPTANCE_REQUIRED, bool());
        validators.put(WORKER_MAX_CONCURRENT, integer(1, 50));
        validators.put(EVALUATION_TIME_LIMIT_SECONDS, integer(1, 3600));
        validators.put(EVALUATION_FUEL_LIMIT, integer(1, 1_000_000_000));
        validators.put(SCORING_PARETO_WEIGHTS, weights());
        validators.put(NOTIFICATIONS_EMAIL, bool());
        validators.put(RETENTION_SUBMISSION_DAYS, integer(1, 3650));
        validators.put(AI_ASSIST_ENABLED, bool());
        validators.put(CONTRACT_AUTO_PUBLISH, bool());
        VALIDATORS = Map.copyOf(validators);
    }

    private ConfigKeys() {
    }

    public static Set<String> all() {
        return VALIDATORS.keySet();
    }

    public static boolean isKnown(String key) {
        return key != null && VALIDATORS.containsKey(key);
    }

    /**
     * A user must not be able to raise their own evaluation limits. GLOBAL is
     * admin-only; ORG and CONTRACT accept only evaluation/retention keys; USER
     * accepts only personal preferences.
     */
    public static boolean allows(ConfigScope scope, String key) {
        return switch (scope) {
            case GLOBAL -> isKnown(key);
            case ORG, CONTRACT -> ORG_KEYS.contains(key);
            case USER -> USER_KEYS.contains(key);
        };
    }

    public static void validate(String key, JsonNode value) {
        Consumer<JsonNode> validator = VALIDATORS.get(key);
        if (validator == null) {
            throw new IllegalArgumentException(
                    "Unknown configuration key '" + key + "'. Known keys: " + all());
        }
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("A value is required for '" + key + "'");
        }
        validator.accept(value);
    }

    private static Consumer<JsonNode> bool() {
        return value -> {
            if (!value.isBoolean()) {
                throw new IllegalArgumentException("Expected a boolean");
            }
        };
    }

    private static Consumer<JsonNode> integer(long min, long max) {
        return value -> {
            if (!value.isIntegralNumber()) {
                throw new IllegalArgumentException("Expected an integer");
            }
            long number = value.asLong();
            if (number < min || number > max) {
                throw new IllegalArgumentException("Expected a value between " + min + " and " + max);
            }
        };
    }

    private static Consumer<JsonNode> weights() {
        return value -> {
            if (!value.isObject()) {
                throw new IllegalArgumentException("Expected an object like {\"time\": 0.5, \"memory\": 0.5}");
            }
            for (String field : new String[] { "time", "memory", "artifact" }) {
                JsonNode weight = value.get(field);
                if (weight != null && (!weight.isNumber() || weight.asDouble() < 0)) {
                    throw new IllegalArgumentException(
                            "Weight '" + field + "' must be a non-negative number");
                }
            }
        };
    }
}
