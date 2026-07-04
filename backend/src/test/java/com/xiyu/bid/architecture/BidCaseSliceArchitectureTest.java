package com.xiyu.bid.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit rules for the AI 案例切片语义检索 feature.
 *
 * <p>These rules enforce the layered architecture agreed in
 * {@code specs/028-ai-case-slice-semantic-search/plan.md}:</p>
 *
 * <ul>
 *   <li>No 2-arg {@code Collectors.toMap} in {@code casework} / {@code ai.client}</li>
 *   <li>Domain/policy packages do not depend on Spring</li>
 *   <li>Controller depends only on application/service packages (plus domain model records used as DTOs)</li>
 *   <li>Application packages do not depend on infrastructure directly</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.xiyu.bid", importOptions = ImportOption.DoNotIncludeTests.class)
public class BidCaseSliceArchitectureTest {

    private static final String CASEWORK_PACKAGE = "com.xiyu.bid.casework";
    private static final String AI_CLIENT_PACKAGE = "com.xiyu.bid.ai.client";

    /**
     * RULE 1: 禁止在 casework / ai.client 包内使用 Collectors.toMap 2 参数版本。
     *
     * <p>2-arg toMap 在重复 key 时抛 IllegalStateException，必须使用 3-arg 版本优雅降级。
     * 本规则复用 Constitution v2.0.0 Principle VII 的语义，但限定在本功能涉及的两个包。</p>
     */
    private static final ArchCondition<JavaClass> NO_TOMAP_2ARG = new ArchCondition<JavaClass>(
        "not call Collectors.toMap with two arguments (no merge function)"
    ) {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                var target = call.getTarget();
                if (target == null) {
                    continue;
                }
                if (!"java.util.stream.Collectors".equals(target.getOwner().getName())) {
                    continue;
                }
                if (!"toMap".equals(target.getName())) {
                    continue;
                }
                // 2-arg overload: (Function, Function)
                if (target.getRawParameterTypes().size() != 2) {
                    continue;
                }
                events.add(SimpleConditionEvent.violated(call,
                    "Collectors.toMap 2-arg overload at " + item.getSimpleName()
                        + ".java:" + call.getLineNumber()
                        + " — add a merge function such as (a, b) -> a"));
            }
        }
    };

    @ArchTest
    public static final ArchRule no2ArgToMapInCaseworkOrAiClient = classes()
        .that().resideInAnyPackage(
            CASEWORK_PACKAGE + "..",
            AI_CLIENT_PACKAGE + ".."
        )
        .should(NO_TOMAP_2ARG)
        .because("Constitution v2.0.0 Principle VII: Collectors.toMap must provide a merge function");

    /**
     * RULE 2: domain/policy 包不得依赖 Spring 框架。
     *
     * <p>纯核心策略与值对象必须无框架依赖，便于单独单元测试。</p>
     */
    @ArchTest
    public static final ArchRule domainPolicyDoesNotDependOnSpring = noClasses()
        .that().resideInAnyPackage(
            CASEWORK_PACKAGE + ".domain..",
            AI_CLIENT_PACKAGE + ".domain.."
        )
        .and().haveSimpleNameNotContaining("Fixture")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "org.springframework.data..")
        .because("Pure core domain/policy classes must not depend on Spring");

    /**
     * RULE 3: casework controller 只允许依赖应用层、领域模型记录（作为响应 DTO）以及少量共享横切类。
     *
     * <p>Controller 保持单薄：所有业务编排下沉到 application.service，所有数据访问下沉到
     * infrastructure。domain.model 中的 record 被直接作为 API 响应体使用，因此显式放行。</p>
     */
    private static final Set<String> CONTROLLER_ALLOWED_TARGET_PREFIXES = Set.of(
        // 本模块应用层
        "com.xiyu.bid.casework.application.",
        "com.xiyu.bid.casework.application.service.",
        // 本模块领域模型（作为响应 DTO）
        "com.xiyu.bid.casework.domain.model.",
        // 共享横切类
        "com.xiyu.bid.dto.ApiResponse",
        "com.xiyu.bid.service.ProjectAccessScopeService",
        "com.xiyu.bid.entity.RoleProfileCatalog",
        // Java 标准库 / Spring / Servlet / Lombok
        "java.",
        "jakarta.",
        "org.springframework.web.",
        "org.springframework.http.",
        "org.springframework.security.access.prepost.PreAuthorize",
        "org.springframework.web.bind.annotation.",
        "lombok."
    );

    private static final ArchCondition<JavaClass> CONTROLLER_ONLY_DEPENDS_ON_ALLOWED_PACKAGES =
        new ArchCondition<JavaClass>("only depend on application/service/domain.model and shared cross-cutting classes") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!item.getPackageName().startsWith(CASEWORK_PACKAGE + ".controller")) {
                    return;
                }
                item.getDirectDependenciesFromSelf().stream()
                    .map(dep -> dep.getTargetClass().getName())
                    .filter(target -> !isAllowedControllerDependency(target))
                    .forEach(target -> events.add(SimpleConditionEvent.violated(item,
                        "Controller " + item.getName() + " depends on disallowed target: " + target)));
            }
        };

    private static boolean isAllowedControllerDependency(String targetName) {
        for (String prefix : CONTROLLER_ALLOWED_TARGET_PREFIXES) {
            if (targetName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @ArchTest
    public static final ArchRule controllerDependsOnlyOnApplicationAndService = classes()
        .that().resideInAPackage(CASEWORK_PACKAGE + ".controller..")
        .and().haveSimpleNameContaining("BidCaseSlice")
        .should(CONTROLLER_ONLY_DEPENDS_ON_ALLOWED_PACKAGES)
        .because("AI case slice controllers must be thin and only depend on application/service/domain.model DTOs and shared cross-cutting classes");

    /**
     * RULE 4: application 包不得直接依赖 infrastructure 包。
     *
     * <p>理想状态下 application 通过 port/adapter 或 repository abstraction 访问基础设施。
     * 当前代码库中部分遗留服务及本功能早期实现直接依赖 JPA 实体/仓库（存放在 infrastructure
     * 包），已列入豁免清单；新加入的 application 类必须遵守此规则。</p>
     */
    private static final Set<String> APPLICATION_INFRASTRUCTURE_EXEMPTIONS = Set.of(
        // 本功能早期已实现的应用服务（直接依赖 JPA 实体/仓库/缓存，按本代码库既有模式）
        "com.xiyu.bid.casework.application.service.BatchEmbeddingAppService",
        "com.xiyu.bid.casework.application.service.BidCaseSliceRecommendAppService",
        "com.xiyu.bid.casework.application.CaseSliceJsonlImporter",
        "com.xiyu.bid.casework.application.BidCaseSliceRecommendationAssembler",
        // 历史遗留归档/知识库相关应用服务（非本功能引入，维持现状）
        "com.xiyu.bid.casework.application.ArchiveFileListService",
        "com.xiyu.bid.casework.application.ProjectArchiveExportService",
        "com.xiyu.bid.casework.application.ProjectArchiveWorkflowService",
        "com.xiyu.bid.casework.application.ProjectArchiveDetailService",
        "com.xiyu.bid.casework.application.ProjectArchiveResponseMapper",
        "com.xiyu.bid.casework.application.StreamingZipPackager",
        "com.xiyu.bid.casework.application.CaseAiMatcher",
        "com.xiyu.bid.casework.application.CaseExportExcelAppService",
        "com.xiyu.bid.casework.application.CasePrecipitationAppService",
        "com.xiyu.bid.casework.application.ProjectClosedEventListener",
        "com.xiyu.bid.casework.application.CaseworkPolicyConfig",
        "com.xiyu.bid.casework.application.service.CaseCrudAppService",
        "com.xiyu.bid.casework.application.service.CaseExportAppService",
        "com.xiyu.bid.casework.application.service.CaseSearchAppService",
        "com.xiyu.bid.casework.application.service.KnowledgeCaseCommandAppService",
        "com.xiyu.bid.casework.application.service.KnowledgeCaseQueryAppService",
        "com.xiyu.bid.casework.application.service.KnowledgeCaseRecommendAppService"
    );

    private static boolean isExemptedFromInfrastructureRule(String className) {
        for (String exempted : APPLICATION_INFRASTRUCTURE_EXEMPTIONS) {
            if (className.equals(exempted) || className.startsWith(exempted + "$")) {
                return true;
            }
        }
        return false;
    }

    private static final ArchCondition<JavaClass> APPLICATION_DOES_NOT_DEPEND_ON_INFRASTRUCTURE =
        new ArchCondition<JavaClass>("not depend directly on infrastructure packages") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!item.getPackageName().startsWith(CASEWORK_PACKAGE + ".application")) {
                    return;
                }
                if (isExemptedFromInfrastructureRule(item.getName())) {
                    return;
                }
                item.getDirectDependenciesFromSelf().stream()
                    .map(dep -> dep.getTargetClass().getPackageName())
                    .filter(pkg -> pkg.startsWith(CASEWORK_PACKAGE + ".infrastructure"))
                    .forEach(pkg -> events.add(SimpleConditionEvent.violated(item,
                        "Application class " + item.getName() + " depends on infrastructure package " + pkg)));
            }
        };

    @ArchTest
    public static final ArchRule applicationDoesNotDependOnInfrastructure = classes()
        .that().resideInAPackage(CASEWORK_PACKAGE + ".application..")
        .should(APPLICATION_DOES_NOT_DEPEND_ON_INFRASTRUCTURE)
        .because("Application layer should not depend directly on infrastructure; use ports/adapters instead");
}
