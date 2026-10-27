package io.symmetry.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitHubRepoRefTest {

    @Test
    void acceptsTheCommonPublicRepositoryForms() {
        assertThat(GitHubRepoRef.parse("https://github.com/acme/code.git"))
                .isEqualTo(new GitHubRepoRef("acme", "code"));
        assertThat(GitHubRepoRef.parse("http://github.com/acme/code"))
                .isEqualTo(new GitHubRepoRef("acme", "code"));
        assertThat(GitHubRepoRef.parse("github.com/acme/code/tree/main/src"))
                .isEqualTo(new GitHubRepoRef("acme", "code"));
        assertThat(GitHubRepoRef.parse("git@github.com:acme/code.git"))
                .isEqualTo(new GitHubRepoRef("acme", "code"));
    }

    @Test
    void buildsACanonicalUrl() {
        assertThat(GitHubRepoRef.parse("https://github.com/acme/code").canonicalUrl())
                .isEqualTo("https://github.com/acme/code");
    }

    @Test
    void rejectsNonGitHubAndMalformedValues() {
        assertThatThrownBy(() -> GitHubRepoRef.parse("https://gitlab.com/acme/code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public GitHub");
        assertThatThrownBy(() -> GitHubRepoRef.parse("https://github.com/acme"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner/repo");
        assertThatThrownBy(() -> GitHubRepoRef.parse("https://github.com/acme/code/extra"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
