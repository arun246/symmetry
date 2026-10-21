package io.symmetry.settings;

/** Where a configuration value applies. Resolution order is USER > CONTRACT > ORG > GLOBAL. */
public enum ConfigScope {
    GLOBAL,
    ORG,
    CONTRACT,
    USER
}
