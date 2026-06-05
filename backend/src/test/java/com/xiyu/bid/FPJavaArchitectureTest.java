// Input: ArchUnit imported production classes
// Output: FP-Java Profile architecture guardrails
// Pos: Test/函数式核心架构门禁
// 一旦我被更新，务必更新 AGENTS.md、RULES.md 与所属文件夹的 md。

package com.xiyu.bid;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FP-Java Profile guardrails.
 *
 * These rules apply to the explicit pure-core package convention:
 * any production class under ..core.. or ..domain.., except ..entity..,
 * is treated as side-effect-free business core.
 *
 * Existing mutable DTO/JPA/service code is not pulled into this gate by
 * accident; new pure business logic should move into these packages when it
 * needs stronger architectural protection.
 */
class FPJavaArchitectureTest {

    private static final List<String> PURE_CORE_PACKAGE_MARKERS = List.of(".core.", ".domain.");

    private static final List<String> EXPECTED_PURE_CORE_PACKAGES = List.of(
        "com.xiyu.bid.marketinsight.core",
        "com.xiyu.bid.admin.settings.core",
        "com.xiyu.bid.task.core",
        "com.xiyu.bid.bidresult.core",
        "com.xiyu.bid.projectworkflow.core",
        "com.xiyu.bid.ai.core",
        "com.xiyu.bid.biddraftagent.domain",
        "com.xiyu.bid.notification.core",
        "com.xiyu.bid.subscription.core",
        "com.xiyu.bid.mention.core",
        "com.xiyu.bid.changetracking.core",
        "com.xiyu.bid.notification.outbound.core",
        "com.xiyu.bid.casework.domain"
        // TODO(bidprocess): add "com.xiyu.bid.bidprocess.core" here as soon as
        // the first non-entity class lands under that package. Until then, the
        // non-empty assertion would red-light on an empty directory.
    );

    private static final List<String> FORBIDDEN_SHELL_PACKAGE_MARKERS = List.of(
        ".controller.",
        ".repository.",
        ".config.",
        ".adapter.",
        ".gateway.",
        ".exception.",
        "org.springframework.web.",
        "org.springframework.data.",
        "org.springframework.jdbc.",
        "jakarta.persistence.",
        "javax.persistence.",
        "org.slf4j.",
        "java.io.",
        "java.nio.file.",
        "java.net."
    );

    private static final List<String> FORBIDDEN_IMPLICIT_INPUTS = List.of(
        "java.lang.System",
        "java.time.Clock",
        "java.util.Random",
        "java.util.concurrent.ThreadLocalRandom"
    );

    private final JavaClasses productionClasses = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.xiyu.bid");

    @Test
    void pure_core_must_not_depend_on_imperative_shell_or_io() {
        List<String> violations = productionClasses.stream()
            .filter(FPJavaArchitectureTest::isPureCoreClass)
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
                .filter(dependency -> targetsForbiddenShellOrIo(javaClass, dependency))
                .map(dependency -> formatDependency(javaClass, dependency)))
            .sorted()
            .toList();

        assertThat(violations)
            .as("Pure core/domain classes must not depend on repository/controller/config/adapter/IO/logging/framework shell APIs")
            .isEmpty();
    }

    @Test
    void pure_core_must_not_depend_on_project_business_exceptions() {
        List<String> violations = productionClasses.stream()
            .filter(FPJavaArchitectureTest::isPureCoreClass)
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
                .filter(dependency -> dependency.getTargetClass().getPackageName().contains(".exception"))
                .map(dependency -> formatDependency(javaClass, dependency)))
            .sorted()
            .toList();

        assertThat(violations)
            .as("Expected business failures in pure core should be returned as Result/Optional/ValidationResult, not project exceptions")
            .isEmpty();
    }

    @Test
    void pure_core_data_must_be_immutable_by_default() {
        List<String> mutableFields = productionClasses.stream()
            .filter(FPJavaArchitectureTest::isPureCoreClass)
            .flatMap(javaClass -> javaClass.getFields().stream()
                .filter(FPJavaArchitectureTest::isMutableInstanceField)
                .map(field -> javaClass.getName() + "#" + field.getName()))
            .sorted()
            .toList();

        assertThat(mutableFields)
            .as("Pure core/domain data should use records or final instance fields")
            .isEmpty();
    }

    @Test
    void pure_core_must_not_expose_setters() {
        List<String> setters = productionClasses.stream()
            .filter(FPJavaArchitectureTest::isPureCoreClass)
            .flatMap(javaClass -> javaClass.getMethods().stream()
                .filter(FPJavaArchitectureTest::isSetter)
                .map(method -> javaClass.getName() + "#" + method.getName()))
            .sorted()
            .toList();

        assertThat(setters)
            .as("Pure core/domain objects should expose state transitions as returned values, not setters")
            .isEmpty();
    }

    @Test
    void pure_core_business_methods_must_return_values() {
        List<String> voidMethods = productionClasses.stream()
            .filter(FPJavaArchitectureTest::isPureCoreClass)
            .flatMap(javaClass -> javaClass.getMethods().stream()
                .filter(FPJavaArchitectureTest::isBusinessMethod)
                .filter(FPJavaArchitectureTest::returnsVoid)
                .map(method -> javaClass.getName() + "#" + method.getName()))
            .sorted()
            .toList();

        assertThat(voidMethods)
            .as("Pure core/domain business methods should return values instead of hiding state changes in void methods")
            .isEmpty();
    }

    @Test
    void pure_core_must_not_use_exceptions_for_business_flow() {
        List<String> exceptionUsages = productionClasses.stream()
            .filter(FPJavaArchitectureTest::isPureCoreClass)
            .flatMap(javaClass -> javaClass.getMethods().stream()
                .filter(FPJavaArchitectureTest::usesExceptions)
                .map(method -> javaClass.getName() + "#" + method.getName()))
            .sorted()
            .toList();

        assertThat(exceptionUsages)
            .as("Pure core/domain business flow should return Result/Optional/ValidationResult instead of throwing or catching exceptions")
            .isEmpty();
    }

    @Test
    void each_expected_pure_core_package_must_contain_at_least_one_class() {
        List<String> emptyPackages = EXPECTED_PURE_CORE_PACKAGES.stream()
            .filter(expectedPackage -> productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(expectedPackage)
                    || javaClass.getPackageName().startsWith(expectedPackage + "."))
                .filter(javaClass -> !javaClass.getPackageName().contains(".entity"))
                .findAny()
                .isEmpty())
            .sorted()
            .toList();

        assertThat(emptyPackages)
            .as("Each declared pure core package must contain at least one non-entity class so FP-Java gates have real coverage")
            .isEmpty();
    }

    private static boolean isPureCoreClass(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return PURE_CORE_PACKAGE_MARKERS.stream().anyMatch(packageName::contains)
            && !packageName.contains(".entity.");
    }

    private static boolean targetsForbiddenShellOrIo(JavaClass originClass, Dependency dependency) {
        if (isCompilerGeneratedEnumValuesArrayCopy(originClass, dependency)) {
            return false;
        }
        String targetName = dependency.getTargetClass().getName();
        return FORBIDDEN_SHELL_PACKAGE_MARKERS.stream().anyMatch(targetName::contains)
            || FORBIDDEN_IMPLICIT_INPUTS.contains(targetName);
    }

    private static boolean isCompilerGeneratedEnumValuesArrayCopy(JavaClass originClass, Dependency dependency) {
        return originClass.isEnum()
            && "java.lang.System".equals(dependency.getTargetClass().getName())
            && dependency.getDescription().contains(".values()> calls method <java.lang.System.arraycopy(");
    }

    private static boolean isMutableInstanceField(JavaField field) {
        return !field.getModifiers().contains(JavaModifier.STATIC)
            && !field.getModifiers().contains(JavaModifier.FINAL);
    }

    private static boolean isSetter(JavaMethod method) {
        return method.getName().startsWith("set")
            && method.getName().length() > 3
            && Character.isUpperCase(method.getName().charAt(3));
    }

    private static boolean isBusinessMethod(JavaMethod method) {
        return !method.getModifiers().contains(JavaModifier.SYNTHETIC)
            && !method.getModifiers().contains(JavaModifier.BRIDGE)
            && !method.getModifiers().contains(JavaModifier.ABSTRACT);
    }

    private static boolean returnsVoid(JavaMethod method) {
        return "void".equals(method.getRawReturnType().getName());
    }

    private static boolean usesExceptions(JavaMethod method) {
        return !method.getExceptionTypes().isEmpty()
            || !method.getTryCatchBlocks().isEmpty()
            || method.getConstructorCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().isAssignableTo(Throwable.class));
    }

    private static String formatDependency(JavaClass javaClass, Dependency dependency) {
        return javaClass.getName() + " -> " + dependency.getTargetClass().getName();
    }

    @Test
    void pure_core_domain_should_prefer_config_over_component() {
        List<String> domainClassesWithComponent = productionClasses.stream()
            .filter(FPJavaArchitectureTest::isPureCoreClass)
            .filter(javaClass -> javaClass.getPackageName().contains(".domain"))
            .filter(javaClass -> javaClass.getAnnotations().stream()
                .anyMatch(ann -> ann.getType().getName().contains("Component")
                    || ann.getType().getName().contains("Service")
                    || ann.getType().getName().contains("Repository")))
            .map(javaClass -> {
                String annotationNames = javaClass.getAnnotations().stream()
                    .map(ann -> ann.getType().getName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
                return javaClass.getName() + " [" + annotationNames + "]";
            })
            .sorted()
            .toList();

        // Warning only (not blocking) — domain/ package classes should prefer
        // @Configuration + @Bean over direct @Component for cleaner separation.
        // See RULES.md §2.5.1 for the three patterns.
        if (!domainClassesWithComponent.isEmpty()) {
            System.out.println("\n[WARN] 以下 domain/ 包纯核心类使用了 @Component/@Service/@Repository 注解：");
            System.out.println("  优先考虑 @Configuration + @Bean 模式（零侵入），");
            System.out.println("  详见 RULES.md §2.5.1 纯核心策略接入 Shell 的三种注册方式。\n");
            for (String cls : domainClassesWithComponent) {
                System.out.println("  " + cls);
            }
            System.out.println();
        }
    }
}
