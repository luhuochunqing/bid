// Input: production Controller/Service source files and project-access guard baseline
// Output: static gate for project-linked backend entry points
// Pos: Test/项目权限门禁
// 一旦我被更新，务必更新 AGENTS.md、RULES.md 与所属文件夹的 md。

package com.xiyu.bid;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ratchet for project-linked data access.
 *
 * The gate scans real backend Controller/Service files. A file is project-linked
 * when it mentions projectId directly, or references a DTO/entity/command type
 * that carries projectId. Project-linked entry points must either call the
 * unified project access guard chain or be listed in the explicit baseline with
 * a reason.
 */
class ProjectAccessGuardCoverageTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Path BASELINE = Paths.get("src/test/resources/project-access-guard-baseline.txt");

    private static final List<String> ACCESS_GUARD_TOKENS = List.of(
            "ProjectAccessScopeService",
            "ProjectLinkedRecordVisibilityPolicy",
            "assertCurrentUserCanAccessProject",
            "filterAccessibleProjects",
            "getAllowedProjectIdsForCurrentUser",
            "currentUserHasAdminAccess",
            "requireProjectAccess",
            "TaskProjectVisibilityPolicy",
            "ExpenseAccessGuard",
            "accessGuard"
    );

    private static final List<String> PROJECT_LINKED_TYPE_PATH_MARKERS = List.of(
            "/dto/",
            "/entity/",
            "/command/",
            "/model/",
            "/view/"
    );

    private static final Pattern PROJECT_ID_TOKEN = Pattern.compile("\\bprojectId\\b");

    @Test
    void project_linked_controller_and_service_files_must_use_guard_or_declared_baseline() {
        Set<String> projectLinkedTypeNames = projectLinkedTypeNames();
        Map<String, String> baseline = readBaseline();
        Map<String, String> candidates = projectLinkedEntryPointSources(projectLinkedTypeNames);

        List<String> unguardedWithoutBaseline = candidates.entrySet().stream()
                .filter(entry -> !usesProjectAccessGuard(entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(path -> !baseline.containsKey(path))
                .toList();

        List<String> staleBaselineEntries = baseline.keySet().stream()
                .filter(path -> !candidates.containsKey(path))
                .toList();

        assertThat(unguardedWithoutBaseline)
                .as("""
                        Every Controller/Service that touches projectId must call the unified project access guard chain
                        or be added to src/test/resources/project-access-guard-baseline.txt with a concrete reason.
                        New unguarded project-linked files:
                        %s
                        """.formatted(String.join("\n", unguardedWithoutBaseline)))
                .isEmpty();
        assertThat(staleBaselineEntries)
                .as("Project access guard baseline contains stale entries that are no longer scanned candidates")
                .isEmpty();
    }

    private static Map<String, String> projectLinkedEntryPointSources(Set<String> projectLinkedTypeNames) {
        Map<String, String> sources = new TreeMap<>();
        javaFiles()
                .filter(ProjectAccessGuardCoverageTest::isControllerOrServiceSource)
                .forEach(path -> {
                    String source = readString(path);
                    if (PROJECT_ID_TOKEN.matcher(source).find() || referencesProjectLinkedType(source, projectLinkedTypeNames)) {
                        sources.put(toRelativePath(path), source);
                    }
                });
        return sources;
    }

    private static Set<String> projectLinkedTypeNames() {
        Set<String> typeNames = new TreeSet<>();
        javaFiles()
                .filter(ProjectAccessGuardCoverageTest::isProjectLinkedDataTypeSource)
                .filter(path -> PROJECT_ID_TOKEN.matcher(readString(path)).find())
                .map(ProjectAccessGuardCoverageTest::simpleClassName)
                .forEach(typeNames::add);
        return typeNames;
    }

    private static boolean isControllerOrServiceSource(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith("Controller.java")
                || fileName.endsWith("Service.java")
                || fileName.endsWith("Guard.java");
    }

    private static boolean isProjectLinkedDataTypeSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return PROJECT_LINKED_TYPE_PATH_MARKERS.stream().anyMatch(normalized::contains);
    }

    private static boolean referencesProjectLinkedType(String source, Set<String> projectLinkedTypeNames) {
        return projectLinkedTypeNames.stream()
                .anyMatch(typeName -> Pattern.compile("\\b" + Pattern.quote(typeName) + "\\b").matcher(source).find());
    }

    private static boolean usesProjectAccessGuard(String source) {
        return ACCESS_GUARD_TOKENS.stream().anyMatch(source::contains);
    }

    private static Map<String, String> readBaseline() {
        if (!Files.exists(BASELINE)) {
            return Map.of();
        }
        Map<String, String> entries = new TreeMap<>();
        try {
            for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.trim().startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", 2);
                if (parts.length != 2 || parts[1].isBlank()) {
                    throw new IllegalStateException("Baseline line must be '<path> | <reason>': " + line);
                }
                entries.put(parts[0].trim(), parts[1].trim());
            }
            return entries;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Stream<Path> javaFiles() {
        try {
            return Files.walk(SOURCE_ROOT)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String simpleClassName(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".java".length());
    }

    private static String toRelativePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
