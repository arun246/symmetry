package io.symmetry.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ToolchainRegistryTest {

    private final ToolchainRegistry registry =
            new ToolchainRegistry(List.of(new JavaToolchain(), new RustToolchain()));

    @Test
    void knowsJavaAndRustCaseInsensitively() {
        assertTrue(registry.supports("java"));
        assertTrue(registry.supports("Rust"));
        assertFalse(registry.supports("python"));
        assertFalse(registry.supports(null));
    }

    @Test
    void requireFailsForUnknownLanguage() {
        assertThrows(IllegalArgumentException.class, () -> registry.require("go"));
    }
}
