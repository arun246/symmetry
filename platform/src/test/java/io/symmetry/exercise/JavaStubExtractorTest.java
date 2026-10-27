package io.symmetry.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class JavaStubExtractorTest {

    private final JavaStubExtractor extractor = new JavaStubExtractor();

    private static final String SOURCE = """
            package demo;

            public class Interval {

                public static int[] merge(int[] left, int[] right) {
                    return left;
                }

                public int size() {
                    return 1;
                }
            }
            """;

    @Test
    void listsMethodsWithRangesInSourceOrder() {
        List<SymbolView> symbols = extractor.symbols(SOURCE);

        assertThat(symbols).extracting(SymbolView::name).containsExactly("merge", "size");
        assertThat(symbols.get(0).kind()).isEqualTo("METHOD");
        assertThat(symbols.get(0).startLine()).isEqualTo(5);
        assertThat(symbols.get(0).endLine()).isEqualTo(7);
        assertThat(symbols.get(0).bodyStartLine()).isEqualTo(5);
        assertThat(symbols.get(0).signature()).contains("merge(int[] left, int[] right)");
    }

    @Test
    void replacesTheBodyAndKeepsTheDeclaration() {
        StubExtraction extraction = extractor.extract(SOURCE,
                new ExtractStubRequest("merge", 5));

        assertThat(extraction.symbolName()).isEqualTo("merge");
        assertThat(extraction.symbolKind()).isEqualTo("METHOD");
        assertThat(extraction.startLine()).isEqualTo(5);
        assertThat(extraction.endLine()).isEqualTo(7);
        assertThat(extraction.stubContent())
                .contains("public static int[] merge(int[] left, int[] right)")
                .contains("UnsupportedOperationException(\"TODO\")")
                .doesNotContain("return left;");
        assertThat(extraction.originalHash()).hasSize(64);
    }

    @Test
    void rejectsAnUnknownSymbolOrWrongLine() {
        assertThatThrownBy(() -> extractor.extract(SOURCE, new ExtractStubRequest("missing", 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
        assertThatThrownBy(() -> extractor.extract(SOURCE, new ExtractStubRequest("merge", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
    }

    @Test
    void rejectsInvalidJava() {
        assertThatThrownBy(() -> extractor.symbols("public class Broken {"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could not be parsed");
    }
}
