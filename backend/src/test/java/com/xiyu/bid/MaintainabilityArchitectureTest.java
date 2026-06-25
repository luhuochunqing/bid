// Input: Java source files and imported production classes
// Output: Maintainability guardrails for split-first services
// Pos: Test/防上帝类门禁
// 一旦我被更新，务必更新 AGENTS.md、RULES.md 与所属文件夹的 md。

package com.xiyu.bid;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Split-First maintainability guardrails.
 *
 * These checks are a ratchet, not a fantasy rewrite. They protect the modules
 * that are already under architectural cleanup and keep new service classes
 * from growing into god classes on their first implementation.
 */
class MaintainabilityArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/xiyu/bid");

    private static final Set<String> PROTECTED_MODULES = Set.of(
        "calendar",
        "collaboration",
        "competitionintel",
        "marketinsight",
        "scoreanalysis",
        "roi",
        "versionhistory",
        "documenteditor",
        "documents",
        "analytics",
        "approval",
        "batch",
        "compliance",
        "export",
        "projectworkflow"
    );

    private static final int MAX_SERVICE_LINES = 300;
    private static final int MAX_SERVICE_DEPENDENCIES = 5;
    private static final int MAX_SERVICE_PUBLIC_METHODS = 8;

    private static final Set<String> SERVICE_LINE_BUDGET_EXEMPTIONS = Set.of(
        "com.xiyu.bid.scoreanalysis.service.ScoreAnalysisService",
        "com.xiyu.bid.roi.service.ROIAnalysisService",
        "com.xiyu.bid.versionhistory.service.VersionHistoryService",
        "com.xiyu.bid.batch.service.BatchOperationService",
        "com.xiyu.bid.projectworkflow.service.ProjectWorkflowService",
        "com.xiyu.bid.projectworkflow.service.ScoreDraftParserService",
        "com.xiyu.bid.approval.service.ApprovalWorkflowService",
        "com.xiyu.bid.export.service.ExcelExportService"
    );

    private static final Set<String> SERVICE_DEPENDENCY_BUDGET_EXEMPTIONS = Set.of(
        "com.xiyu.bid.batch.service.BatchOperationService",
        "com.xiyu.bid.batch.service.BatchTenderAssignAppService",
        "com.xiyu.bid.export.service.ExcelExportService",
        "com.xiyu.bid.scoreanalysis.service.ScoreAnalysisService",
        "com.xiyu.bid.projectworkflow.service.ProjectTaskWorkflowService"
    );

    private static final Set<String> SERVICE_PUBLIC_METHOD_BUDGET_EXEMPTIONS = Set.of(
        "com.xiyu.bid.collaboration.service.CollaborationService",
        "com.xiyu.bid.documenteditor.service.DocumentEditorService",
        "com.xiyu.bid.analytics.service.DashboardAnalyticsService",
        "com.xiyu.bid.approval.service.ApprovalWorkflowService",
        "com.xiyu.bid.projectworkflow.service.ProjectWorkflowService"
    );

    private final JavaClasses productionClasses = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.xiyu.bid");

    @Test
    void protected_service_files_should_stay_under_line_budget() throws IOException {
        List<String> violations;
        try (var paths = Files.walk(SOURCE_ROOT)) {
            violations = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith("Service.java"))
                .filter(path -> isProtectedModuleServicePath(path))
                .map(path -> new ServiceSourceStat(path, toClassName(path), countLines(path)))
                .filter(stat -> stat.lines() > MAX_SERVICE_LINES)
                .filter(stat -> !SERVICE_LINE_BUDGET_EXEMPTIONS.contains(stat.className()))
                .map(stat -> stat.className() + " -> " + stat.lines() + " lines")
                .sorted()
                .toList();
        }

        assertThat(violations)
            .as("Protected services should be split before they exceed the line budget")
            .isEmpty();
    }

    @Test
    void protected_services_should_keep_dependency_count_small() {
        List<String> violations = protectedServiceClasses().stream()
            .map(javaClass -> new ServiceBudgetStat(javaClass.getName(), countCollaborators(javaClass)))
            .filter(stat -> stat.count() > MAX_SERVICE_DEPENDENCIES)
            .filter(stat -> !SERVICE_DEPENDENCY_BUDGET_EXEMPTIONS.contains(stat.className()))
            .map(stat -> stat.className() + " -> " + stat.count() + " collaborators")
            .sorted()
            .toList();

        assertThat(violations)
            .as("Protected services should keep collaborator count small enough to force Split-First decomposition")
            .isEmpty();
    }

    @Test
    void protected_services_should_keep_public_surface_small() {
        List<String> violations = protectedServiceClasses().stream()
            .map(javaClass -> new ServiceBudgetStat(javaClass.getName(), countPublicMethods(javaClass)))
            .filter(stat -> stat.count() > MAX_SERVICE_PUBLIC_METHODS)
            .filter(stat -> !SERVICE_PUBLIC_METHOD_BUDGET_EXEMPTIONS.contains(stat.className()))
            .map(stat -> stat.className() + " -> " + stat.count() + " public methods")
            .sorted()
            .toList();

        assertThat(violations)
            .as("Protected services should keep a small public surface instead of collecting multiple use cases")
            .isEmpty();
    }

    private List<JavaClass> protectedServiceClasses() {
        return productionClasses.stream()
            .filter(MaintainabilityArchitectureTest::isProtectedServiceClass)
            .sorted((left, right) -> left.getName().compareTo(right.getName()))
            .toList();
    }

    private static boolean isProtectedModuleServicePath(Path path) {
        Path relativePath = SOURCE_ROOT.relativize(path);
        if (relativePath.getNameCount() < 3) {
            return false;
        }
        String moduleName = relativePath.getName(0).toString();
        return PROTECTED_MODULES.contains(moduleName)
            && "service".equals(relativePath.getName(1).toString());
    }

    private static boolean isProtectedServiceClass(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.startsWith("com.xiyu.bid.")
            && packageName.contains(".service")
            && PROTECTED_MODULES.stream().anyMatch(module -> packageName.startsWith("com.xiyu.bid." + module + ".service"));
    }

    private static int countLines(Path path) {
        try (var lines = Files.lines(path)) {
            return (int) lines.count();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to count lines for " + path, exception);
        }
    }

    private static String toClassName(Path path) {
        String relativePath = SOURCE_ROOT.relativize(path).toString();
        String dottedPath = relativePath.replace(path.getFileSystem().getSeparator(), ".");
        return "com.xiyu.bid." + dottedPath.substring(0, dottedPath.length() - ".java".length());
    }

    private static int countCollaborators(JavaClass javaClass) {
        return (int) javaClass.getFields().stream()
            .filter(MaintainabilityArchitectureTest::isInstanceField)
            .count();
    }

    private static int countPublicMethods(JavaClass javaClass) {
        return (int) javaClass.getMethods().stream()
            .filter(method -> method.getOwner().equals(javaClass))
            .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
            .filter(method -> !method.getName().startsWith("lambda$"))
            .filter(method -> !method.getName().equals("equals"))
            .filter(method -> !method.getName().equals("hashCode"))
            .filter(method -> !method.getName().equals("toString"))
            .count();
    }

    private static boolean isInstanceField(JavaField field) {
        return !field.getModifiers().contains(JavaModifier.STATIC);
    }

    private record ServiceSourceStat(Path path, String className, int lines) {
    }

    private record ServiceBudgetStat(String className, int count) {
    }
}
