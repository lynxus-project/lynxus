package io.github.lynxus.test.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins hard architecture bans so a feature cannot recast them in place.
 * Change the source section and the golden together, only with an explicit decision.
 */
class NonNegotiableArchitectureTextTest {

    @Test
    void agentsNonNegotiableArchitectureMatchesGolden() throws IOException {
        assertSectionMatches(
            "AGENTS.md",
            "## Non-Negotiable Architecture",
            "## Agent Tooling Policy",
            "architecture/agents-non-negotiable-architecture.md");
    }

    @Test
    void designPhilosophyExplicitNonGoalsMatchGolden() throws IOException {
        assertSectionMatches(
            "Design-Philosophy.md",
            "## Explicit Non-Goals",
            "## Evidence Before Claims",
            "architecture/design-philosophy-explicit-non-goals.md");
    }

    @Test
    void coreContractDoesNotOwnMatchesGolden() throws IOException {
        assertSectionMatches(
            "docs/reference/core-contract.md",
            "Core does not own:",
            "Spring integration is a host adapter.",
            "architecture/core-contract-does-not-own.md");
    }

    @Test
    void coreContractGaNonGoalsMatchGolden() throws IOException {
        assertSectionMatches(
            "docs/reference/core-contract.md",
            "## 9. Explicit Non-Goals For Core GA",
            null,
            "architecture/core-contract-ga-non-goals.md");
    }

    private void assertSectionMatches(
            String sourceRelativePath,
            String startHeading,
            String endHeading,
            String goldenClasspath) throws IOException {
        Path root = repositoryRoot();
        String source = Files.readString(root.resolve(sourceRelativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
        int start = source.indexOf(startHeading);
        assertTrue(start >= 0, "missing heading in " + sourceRelativePath + ": " + startHeading);
        int end = endHeading == null ? source.length() : source.indexOf(endHeading, start);
        assertTrue(endHeading == null || end >= 0, "missing end heading in " + sourceRelativePath + ": " + endHeading);
        String actual = source.substring(start, end).strip() + "\n";
        var golden = getClass().getClassLoader().getResourceAsStream(goldenClasspath);
        assertNotNull(golden, "missing golden " + goldenClasspath);
        String expected = new String(golden.readAllBytes(), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
        if (!expected.endsWith("\n")) {
            expected = expected + "\n";
        }
        assertEquals(expected, actual, sourceRelativePath + " hard section drifted from golden");
    }

    private Path repositoryRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("AGENTS.md"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("Cannot locate repository root from " + Path.of("").toAbsolutePath());
        }
        return directory;
    }
}
