package io.symmetry.exercise;

import java.util.List;

public interface StubExtractor {

    String language();

    List<SymbolView> symbols(String source);

    StubExtraction extract(String source, ExtractStubRequest request);
}
