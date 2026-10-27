package io.symmetry.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class StaticCheckServiceTest {

    private final StaticCheckService checks = new StaticCheckService();

    private static Exercise exercise(String language) {
        return new Exercise(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Interval merge", "Merge two sorted arrays", language, "MEDIUM",
                1500, "USD", 7, "");
    }

    @Test
    void passesCleanJava() {
        StaticCheckResult result = checks.check(exercise("java"), """
                class Interval {
                    static int size() {
                        return 1;
                    }
                }
                """);

        assertThat(result.passed()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void reportsSyntaxErrorsAndForbiddenApis() {
        StaticCheckResult syntax = checks.check(exercise("java"), "class Broken {");
        assertThat(syntax.passed()).isFalse();
        assertThat(syntax.findings()).anyMatch(value -> value.startsWith("Java syntax error:"));

        StaticCheckResult forbidden = checks.check(exercise("java"), """
                class Escape {
                    void run() throws Exception {
                        Runtime.getRuntime().exec("id");
                        new java.io.File("x");
                    }
                }
                """);
        assertThat(forbidden.passed()).isFalse();
        assertThat(forbidden.findings()).contains(
                "Forbidden API: Runtime.getRuntime",
                "Forbidden API: java.io.File");
    }

    @Test
    void rejectsBlankInput() {
        StaticCheckResult result = checks.check(exercise("java"), "  ");

        assertThat(result.passed()).isFalse();
        assertThat(result.findings()).containsExactly("Code is empty.");
    }

    @Test
    void appliesLanguageSpecificRules() {
        StaticCheckResult python = checks.check(exercise("python"), "import subprocess\nprint(1)");
        assertThat(python.passed()).isFalse();
        assertThat(python.findings()).contains("Forbidden API: import subprocess");

        StaticCheckResult rust = checks.check(exercise("rust"), "fn main() { std::fs::read(\"x\"); }");
        assertThat(rust.passed()).isFalse();
        assertThat(rust.findings()).contains("Forbidden API: std::fs");
    }
}
