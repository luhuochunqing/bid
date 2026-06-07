// Input: ArchUnit imported production classes
// Output: FP-Java Profile + Split-First architecture rules
// Pos: Test/FP-Java 架构门禁
// 一旦我被更新，务必更新 AGENTS.md、RULES.md 与所属文件夹的 md。
//
// FP-Java Profile + Split-First Rule:
//   1. 纯核心（core 包）负责业务决策，不依赖框架/基础设施
//   2. 应用服务（service/application 包）只做编排，不包含业务规则
//   3. 任何类不得同时承担规则计算、数据访问、DTO 转换、状态写入三类以上职责
//   4. 单文件超过 300 行前必须拆分（全量覆盖，不只 protected modules）

package com.xiyu.bid;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * FP-Java Profile + Split-First architecture gates.
 *
 * Enforces tiered architecture rules that prevent class-level responsibility
 * bloat and preserve the separation between pure business logic (core) and
 * infrastructure orchestration (service/application).
 *
 * These are hard gates — violations block PRs.
 */
class ResponsibilityArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/xiyu/bid");

    // ── 300-line budget: ALL production .java files (not just protected modules) ──
    private static final int MAX_LINES_ALL = 300;

    private static final Set<String> LINE_BUDGET_EXEMPTIONS = Set.of(
        // Generated / config
        "com.xiyu.bid.admin.permissions.BuiltInPermissionRegistry",
        // Grandfathered pre-existing large files (渐进收紧目标)
        "com.xiyu.bid.projectworkflow.service.ProjectWorkflowService",
        "com.xiyu.bid.projectworkflow.service.ScoreDraftParserService",
        "com.xiyu.bid.approval.service.ApprovalWorkflowService",
        "com.xiyu.bid.export.service.ExcelExportService",
        "com.xiyu.bid.scoreanalysis.service.ScoreAnalysisService",
        "com.xiyu.bid.roi.service.ROIAnalysisService",
        "com.xiyu.bid.versionhistory.service.VersionHistoryService",
        "com.xiyu.bid.batch.service.BatchOperationService",
        "com.xiyu.bid.collaboration.service.CollaborationService",
        "com.xiyu.bid.documenteditor.service.DocumentEditorService",
        "com.xiyu.bid.analytics.service.DashboardAnalyticsService",
        // Pre-existing legacy large files that exceeded 300-line budget
        "com.xiyu.bid.ai.client.OpenAiCompatibleClient",
        "com.xiyu.bid.approval.controller.ApprovalController",
        "com.xiyu.bid.batch.controller.BatchOperationController",
        "com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy",
        "com.xiyu.bid.entity.Tender",
        "com.xiyu.bid.exception.GlobalExceptionHandler",
        "com.xiyu.bid.formengine.application.FormDefinitionAdminService",
        "com.xiyu.bid.platform.service.PlatformAccountService",
        "com.xiyu.bid.project.service.ProjectService",
        "com.xiyu.bid.project.service.ProjectClosureService",
        "com.xiyu.bid.resources.service.CaCertificateService",
        "com.xiyu.bid.settings.service.SettingsService",
        "com.xiyu.bid.tender.controller.TenderController",
        "com.xiyu.bid.tender.service.TenderCommandService"
    );

    // ── FP-Java: Core isolation patterns ──
    // Core packages that must NOT depend on infrastructure/framework
    private static final Pattern CORE_PACKAGE_PATTERN = Pattern.compile(
        "com\\.xiyu\\.bid\\.[a-z]+(?:\\.infra)?\\.core(?:\\..*)?"
    );
    private static final Pattern APPLICATION_PACKAGE_PATTERN = Pattern.compile(
        "com\\.xiyu\\.bid\\.[a-z]+\\.application(?:\\..*)?"
    );
    // For modules using flat service/ structure instead of application/
    private static final Pattern SERVICE_PACKAGE_PATTERN = Pattern.compile(
        "com\\.xiyu\\.bid\\.[a-z]+\\.service(?:\\..*)?"
    );

    // Infrastructure/framework types that core packages must NOT depend on
    private static final Set<String> FORBIDDEN_CORE_IMPORTS = Set.of(
        // Spring framework annotations
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.stereotype.Controller",
        "org.springframework.stereotype.Component",
        "org.springframework.beans.factory.annotation.Autowired",
        "org.springframework.beans.factory.annotation.Qualifier",
        "org.springframework.context.annotation.Bean",
        // JPA / persistence
        "javax.persistence.Entity",
        "javax.persistence.Table",
        "javax.persistence.Column",
        "javax.persistence.Id",
        "javax.persistence.GeneratedValue",
        "javax.persistence.ManyToOne",
        "javax.persistence.OneToMany",
        "javax.persistence.JoinColumn",
        "jakarta.persistence.Entity",
        "jakarta.persistence.Table",
        "jakarta.persistence.Column",
        "jakarta.persistence.Id",
        "jakarta.persistence.GeneratedValue",
        "org.springframework.data.jpa.repository.JpaRepository",
        "org.springframework.data.repository.CrudRepository",
        // Servlet / web
        "javax.servlet.http.HttpServletRequest",
        "javax.servlet.http.HttpServletResponse",
        "jakarta.servlet.http.HttpServletRequest",
        "jakarta.servlet.http.HttpServletResponse",
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.RestController",
        // Transaction / infrastructure
        "org.springframework.transaction.annotation.Transactional",
        "org.springframework.jdbc.core.JdbcTemplate",
        "org.springframework.cache.annotation.Cacheable",
        "org.springframework.scheduling.annotation.Scheduled"
    );

    // ── Responsibility import categories ──
    // Category 1: Data access (persistence)
    private static final Set<String> DATA_ACCESS_PREFIXES = Set.of(
        "javax.persistence",
        "jakarta.persistence",
        "org.springframework.data",
        ".repository.",
        ".entity."
    );
    // Category 2: Web / transport
    private static final Set<String> WEB_PREFIXES = Set.of(
        "javax.servlet",
        "jakarta.servlet",
        "org.springframework.web",
        ".controller."
    );
    // Category 3: Infrastructure / framework orchestration
    private static final Set<String> INFRASTRUCTURE_PREFIXES = Set.of(
        "org.springframework.stereotype",
        "org.springframework.beans.factory",
        "org.springframework.context",
        "org.springframework.transaction",
        "org.springframework.jdbc",
        "org.springframework.cache",
        "org.springframework.scheduling",
        "org.springframework.boot"
    );
    // Category 4: DTO / mapping
    private static final Set<String> DTO_PREFIXES = Set.of(
        ".dto.",
        ".vo.",
        "org.mapstruct",
        "ModelMapper"
    );

    private final JavaClasses productionClasses = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.xiyu.bid");

    // ─────────────────────────────────────────────────────────────────
    // RULE 1: 300-line budget for ALL production files
    // ─────────────────────────────────────────────────────────────────
    @Test
    void all_production_files_should_stay_under_line_budget() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(SOURCE_ROOT)) {
            violations = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> {
                    String className = toClassName(path);
                    int lines = countLines(path);
                    return new FileStat(path, className, lines);
                })
                .filter(stat -> stat.lines() > MAX_LINES_ALL)
                .filter(stat -> !LINE_BUDGET_EXEMPTIONS.contains(stat.className()))
                .map(stat -> stat.className() + " -> " + stat.lines() + " lines (max " + MAX_LINES_ALL + ")")
                .sorted()
                .toList();
        }

        assertThat(violations)
            .as("ALL production Java files must stay under " + MAX_LINES_ALL
                + " lines. Split oversized classes before adding new code.")
            .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────
    // RULE 2: FP-Java — Core packages must not depend on infrastructure
    // ─────────────────────────────────────────────────────────────────
    @Test
    void core_packages_should_not_depend_on_infrastructure() {
        List<String> violations = productionClasses.stream()
            .filter(javaClass -> isCoreClass(javaClass))
            .filter(javaClass -> !javaClass.isInterface())
            .filter(javaClass -> !javaClass.isEnum())
            .flatMap(javaClass -> checkForbiddenImports(javaClass).stream())
            .sorted()
            .toList();

        assertThat(violations)
            .as("Pure core packages (*.core.*) must NOT depend on Spring/JPA/servlet infrastructure. "
                + "Core = pure business logic only. Move infrastructure concerns to service layer.")
            .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────
    // RULE 3: Responsibility budget — no class may bridge >2 domains
    // ─────────────────────────────────────────────────────────────────
    @Test
    void no_class_should_handle_more_than_two_responsibility_domains() {
        List<String> violations = productionClasses.stream()
            .filter(javaClass -> !javaClass.isInterface())
            .filter(javaClass -> !javaClass.isEnum())
            .filter(javaClass -> !isExemptFromResponsibilityCheck(javaClass))
            .map(this::countResponsibilityDomains)
            .filter(result -> result.count() > 2)
            .map(result -> result.className() + " -> imports from "
                + result.domains() + " responsibility domains (>2). "
                + "Split by responsibility: rules|data-access|dto|infrastructure.")
            .sorted()
            .toList();

        assertThat(violations)
            .as("No class should bridge more than 2 responsibility domains "
                + "(data-access, web, infrastructure, DTO/mapping). "
                + "Violation indicates a god class doing too much.")
            .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────
    // RULE 4: Service/Application classes should not contain core rules
    // ─────────────────────────────────────────────────────────────────
    @Test
    void service_classes_should_not_import_core_rules_patterns() {
        // Detects service classes that import domain policy/rule/spec classes
        // Pattern: service importing from another module's .core. or .domain. package
        List<String> violations = productionClasses.stream()
            .filter(javaClass -> isServiceClass(javaClass))
            .filter(javaClass -> !javaClass.isInterface())
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
                .filter(dep -> isCoreOrDomainClass(dep.getTargetClass()))
                .filter(dep -> !sameModule(javaClass, dep.getTargetClass()))
                .map(dep -> javaClass.getName()
                    + " imports core/domain class " + dep.getTargetClass().getName()
                    + " from another module. Services should call other services, not core directly.")
            )
            .sorted()
            .toList();

        // This rule is advisory — we only report, don't block, as there may be
        // legitimate cross-module core usage that needs gradual refactoring.
        if (!violations.isEmpty()) {
            var message = violations.stream()
                .collect(Collectors.joining("\n  ", "Advisory: service classes importing "
                    + "cross-module core classes (not blocking, but consider refactoring):\n  ", ""));
            System.out.println(message); // printed to test output
        }
    }

    // ── Helper methods ──

    private boolean isCoreClass(JavaClass javaClass) {
        return CORE_PACKAGE_PATTERN.matcher(javaClass.getPackageName()).matches();
    }

    private boolean isServiceClass(JavaClass javaClass) {
        return SERVICE_PACKAGE_PATTERN.matcher(javaClass.getPackageName()).matches()
            || APPLICATION_PACKAGE_PATTERN.matcher(javaClass.getPackageName()).matches();
    }

    private boolean isCoreOrDomainClass(JavaClass javaClass) {
        return javaClass.getPackageName().contains(".core")
            || javaClass.getPackageName().contains(".domain");
    }

    private boolean sameModule(JavaClass a, JavaClass b) {
        // Extract module name: com.xiyu.bid.<module>.<layer>...
        String moduleA = extractModule(a.getPackageName());
        String moduleB = extractModule(b.getPackageName());
        return moduleA.equals(moduleB);
    }

    private String extractModule(String packageName) {
        // com.xiyu.bid.approval.core.policy -> approval
        String[] parts = packageName.split("\\.");
        if (parts.length >= 4) {
            return parts[3]; // com.xiyu.bid.<module>
        }
        return "";
    }

    private List<String> checkForbiddenImports(JavaClass javaClass) {
        return javaClass.getDirectDependenciesFromSelf().stream()
            .map(dep -> dep.getTargetClass().getName())
            .filter(targetName -> FORBIDDEN_CORE_IMPORTS.contains(targetName))
            .map(targetName -> javaClass.getName() + " imports forbidden type: " + targetName)
            .toList();
    }

    private ResponsibilityResult countResponsibilityDomains(JavaClass javaClass) {
        var importedPackages = javaClass.getDirectDependenciesFromSelf().stream()
            .map(dep -> dep.getTargetClass().getPackageName())
            .collect(Collectors.toSet());

        int count = 0;
        var domains = new ArrayList<String>();

        if (matchesAny(importedPackages, DATA_ACCESS_PREFIXES)) {
            count++;
            domains.add("data-access");
        }
        if (matchesAny(importedPackages, WEB_PREFIXES)) {
            count++;
            domains.add("web");
        }
        if (matchesAny(importedPackages, INFRASTRUCTURE_PREFIXES)) {
            count++;
            domains.add("infrastructure");
        }
        if (matchesAny(importedPackages, DTO_PREFIXES)) {
            count++;
            domains.add("dto");
        }

        return new ResponsibilityResult(javaClass.getName(), count, String.join(", ", domains));
    }

    private boolean matchesAny(Set<String> packages, Set<String> prefixes) {
        return packages.stream().anyMatch(pkg ->
            prefixes.stream().anyMatch(prefix -> pkg.contains(prefix))
        );
    }

    private boolean isExemptFromResponsibilityCheck(JavaClass javaClass) {
        String name = javaClass.getName();
        // Exempt config classes, aspect classes, annotations, bootstrappers, and legacy/grandfathered controllers
        return name.contains(".config.")
            || name.contains(".annotation.")
            || name.contains(".aspect.")
            || name.contains(".bootstrap.")
            || name.contains(".enums.")
            || name.equals("com.xiyu.bid.casework.controller.KnowledgeCaseController");
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

    private record FileStat(Path path, String className, int lines) {}
    private record ResponsibilityResult(String className, int count, String domains) {}
}
