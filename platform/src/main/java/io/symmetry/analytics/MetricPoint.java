package io.symmetry.analytics;

/** One labelled value for a donut, bar or line chart. */
public record MetricPoint(String label, long value) {
}
