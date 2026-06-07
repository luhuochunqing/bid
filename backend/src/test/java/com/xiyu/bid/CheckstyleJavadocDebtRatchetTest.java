// Input: checkstyle-result.xml report (generated under -Pjava-quality) + javadoc-violation-baseline.txt
// Output: ratchet gate preventing increase of visible Javadoc/MissingJavadoc violations (post-suppressions)
// Pos: backend static quality / Javadoc 历史债门禁
// 一旦我被更新，务必同步 implementation-notes.md（Checkstyle Javadoc 37k 历史债 + 修直过程）、backend/QUALITY_GATE_GUIDE.md、suppressions.xml 注释。

package com.xiyu.bid;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ratchet for pre-existing Javadoc missing violations (the 37k+ historical debt).
 *
 * The 37,338 number reported by the user came from full/unscoped scans (plain mvn checkstyle:checkstyle,
 * IDE "whole project", or -Dquality.includes="** /*.java"). These are grandfathered.
 *
 * Strategy (see suppressions.xml comment + implementation-notes "修直过程"):
 * - A broad blanket suppress in suppressions.xml makes even full scans report 0 (or tiny) Javadoc* violations.
 * - The modules (MissingJavadocMethod/Type) are active so the rule can be selectively enforced later
 *   by carving protected sub-trees out of the suppress (negative lookahead) during a deliberate 扩圈 step.
 * - This test ensures the *visible* (post-suppress) count never increases without an explicit baseline update
 *   + reason. It is the machine equivalent of the L3 historical debt handling in QUALITY_GATE_GUIDE.
 *
 * Baseline starts at 0 after the initial broad suppress. It only changes when:
 * - Real (non-placeholder) Javadoc is added to a carved-out protected core (number goes down).
 * - A carve-out PR intentionally exposes debt for an area (number may go up temporarily, then the same
 *   or predecessor PR must bring it back down; baseline.txt is updated with explanation).
 *
 * To activate the ratchet (instead of skip), first explicitly run a wide report (see
 * implementation-notes.md 修直过程 for the exact command and why we avoided pom binding).
 */
class CheckstyleJavadocDebtRatchetTest {

    private static final Path REPORT = Paths.get("target/checkstyle-result.xml");
    private static final Path BASELINE = Paths.get("src/test/resources/javadoc-violation-baseline.txt");

    @Test
    void visible_javadoc_violations_must_not_exceed_baseline() {
        if (!Files.exists(REPORT)) {
            // Normal dev `mvn test` (no -Pjava-quality) or report not yet generated.
            // The ratchet is only meaningful under the quality profile (CI jobs, pre-commit -P, local quality verify).
            System.out.println("[JavadocRatchet] REPORT not found at " + REPORT +
                    " — skipping (run the explicit wide report command first, see class javadoc for details).");
            return;
        }

        int visibleJavadocErrors;
        try {
            String xml = Files.readString(REPORT, StandardCharsets.UTF_8);
            // The report contains <error ... source="...MissingJavadoc..." ... /> or message containing the check name.
            // We count occurrences of the known check names (case-insensitive for robustness).
            visibleJavadocErrors = countJavadocErrors(xml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        int baseline = readBaselineCount();

        assertThat(visibleJavadocErrors)
                .as("""
                        Visible Javadoc/MissingJavadoc violations (after suppressions.xml blanket) must not exceed baseline.
                        Current visible: %d
                        Baseline: %d
                        If this is a new increase: either (a) add real Javadoc to the offending public API in a protected core,
                        or (b) update %s with the new number + date + concrete reason (see carve-out process in suppressions.xml comment).
                        Historical 37,338 was the pre-suppress full-repo number; we track only the *visible* post-suppress count.
                        """.formatted(visibleJavadocErrors, baseline, BASELINE))
                .isLessThanOrEqualTo(baseline);
    }

    private static int countJavadocErrors(String xml) {
        // Simple, dependency-free count. The checkstyle report format is stable:
        // <error ... source="com.puppycrawl.tools.checkstyle.checks.javadoc.MissingJavadocMethodCheck" .../>
        // or message may contain "Missing Javadoc".
        String lower = xml.toLowerCase(Locale.ROOT);
        int count = 0;
        count += countOccurrences(lower, "missingjavadocmethod");
        count += countOccurrences(lower, "missingjavadoctype");
        count += countOccurrences(lower, "javadocvariable");
        count += countOccurrences(lower, "javadoctype");
        count += countOccurrences(lower, "javadocmethod");
        count += countOccurrences(lower, "javadocpackage");
        count += countOccurrences(lower, "javadocstyle");
        return count;
    }

    private static int countOccurrences(String haystack, String needle) {
        int idx = 0;
        int cnt = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            cnt++;
            idx += needle.length();
        }
        return cnt;
    }

    private static int readBaselineCount() {
        if (!Files.exists(BASELINE)) {
            // Start strict at 0 if no baseline file yet (first run after adding the rule).
            return 0;
        }
        try {
            for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.trim().startsWith("#")) {
                    continue;
                }
                // Accept "0" or "0 | 2026-06 | reason..."
                String numPart = line.split("\\|", 2)[0].trim();
                return Integer.parseInt(numPart);
            }
            return 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("javadoc-violation-baseline.txt first non-comment line must start with an integer: " + e.getMessage(), e);
        }
    }
}