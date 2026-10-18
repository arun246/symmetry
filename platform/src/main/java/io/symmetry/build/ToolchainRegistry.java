package io.symmetry.build;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The toolchain registry (socket 6). Discovers every {@link Toolchain} bean and
 * maps it by language, so supporting a new language is a matter of adding a bean.
 *
 * <p>Note that {@link #supports(String)} means "the platform knows this language"
 * (so a contract may be published for it), independent of whether the pinned
 * compiler is installed on the machine running the API.
 */
@Component
public class ToolchainRegistry {

    private final Map<String, Toolchain> byLanguage;

    public ToolchainRegistry(List<Toolchain> toolchains) {
        Map<String, Toolchain> map = new LinkedHashMap<>();
        for (Toolchain toolchain : toolchains) {
            map.put(toolchain.language().toLowerCase(), toolchain);
        }
        this.byLanguage = Map.copyOf(map);
    }

    public boolean supports(String language) {
        return language != null && byLanguage.containsKey(language.toLowerCase());
    }

    public Set<String> supported() {
        return byLanguage.keySet();
    }

    public Toolchain require(String language) {
        Toolchain toolchain = language == null ? null : byLanguage.get(language.toLowerCase());
        if (toolchain == null) {
            throw new IllegalArgumentException("Unsupported language '" + language
                    + "'. Known languages: " + byLanguage.keySet());
        }
        return toolchain;
    }
}
