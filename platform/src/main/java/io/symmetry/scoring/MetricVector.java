package io.symmetry.scoring;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * A flexible bundle of named measurements.
 *
 * <p>Rather than fixed fields (p50/p99/memory/...), metrics are a map so new
 * domains can record jobs-per-second, page-feel, answer quality, energy, and so
 * on without a schema change. V1 records the compute numbers it knows.
 */
public record MetricVector(Map<String, Double> metrics) {

    public MetricVector {
        metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
    }

    public static MetricVector empty() {
        return new MetricVector(Map.of());
    }

    public static MetricVector of(Map<String, Double> metrics) {
        return new MetricVector(metrics);
    }

    public static MetricVector of(String name, double value) {
        return new MetricVector(Map.of(name, value));
    }

    public OptionalDouble get(String name) {
        Double value = metrics.get(name);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public MetricVector with(String name, double value) {
        Map<String, Double> merged = new LinkedHashMap<>(metrics);
        merged.put(name, value);
        return new MetricVector(merged);
    }
}
