package io.symmetry.build;

import org.springframework.stereotype.Component;

/**
 * Rust toolchain placeholder. Modules compile to {@code wasm32-unknown-unknown}
 * and run in Chicory (M3). For now the connector only declares the language; the
 * compiler is absent on the development host, so {@link #available()} is false.
 */
@Component
public class RustToolchain implements Toolchain {

    @Override
    public String language() {
        return "rust";
    }

    @Override
    public boolean available() {
        return false;
    }
}
