package io.symmetry.build;

import org.springframework.stereotype.Component;

/**
 * Java toolchain placeholder. Compilation (fixed {@code javac} 21, fingerprinting,
 * reproducibility check) is implemented in M3; for now the connector only declares
 * the language and reports whether the pinned JDK looks present.
 */
@Component
public class JavaToolchain implements Toolchain {

    private static final String PINNED = "21";

    @Override
    public String language() {
        return "java";
    }

    @Override
    public String toolVersion() {
        return PINNED;
    }

    @Override
    public boolean available() {
        return System.getProperty("java.version", "").startsWith(PINNED);
    }
}
