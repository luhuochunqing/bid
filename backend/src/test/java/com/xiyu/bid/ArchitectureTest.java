// Input: ArchUnit framework
// Output: Architecture validation rules
// Pos: Test/架构测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.Slice;

import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture Tests for XiYu Bid Platform
 *
 * Enforces layered architecture and prevents dependency violations.
 * Run with: mvn test -Dtest=ArchitectureTest
 *
 * Violations will block the build - this is intentional (J4: Reflex).
 *
 * ============= 分阶段实施策略 (Phase C) =============
 * 新模块 (2026-03-04起): 严格遵守所有架构规则
 *   - calendar, collaboration, competitionintel, scoreanalysis
 *   - roi, versionhistory, documenteditor, documents
 *
 * 老模块 (POC阶段): 暂时豁免，后续逐步重构
 *   - auth, tender, project, task, qualification, case, template
 *   - fee, platform, compliance, dashboard, alerts, resources
 *
 * 测试类: 通过@AnalyzeClasses的importOption排除
 * =============
 */
@AnalyzeClasses(
    packages = "com.xiyu.bid",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    private static final String[] STRICT_CONTROLLER_PACKAGES = {
        "com.xiyu.bid.calendar.controller..",
        "com.xiyu.bid.collaboration.controller..",
        "com.xiyu.bid.competitionintel.controller..",
        "com.xiyu.bid.scoreanalysis.controller..",
        "com.xiyu.bid.roi.controller..",
        "com.xiyu.bid.versionhistory.controller..",
        "com.xiyu.bid.documenteditor.controller..",
        "com.xiyu.bid.documentexport.controller..",
        "com.xiyu.bid.documents.controller..",
        "com.xiyu.bid.settings.controller..",
        "com.xiyu.bid.fees.controller..",
        "com.xiyu.bid.projectworkflow.controller..",
        "com.xiyu.bid.resources.controller..",
        "com.xiyu.bid.casework.controller..",
        "com.xiyu.bid.analytics.controller.."
    };

    private static final String[] DTO_READY_CONTROLLER_PACKAGES = {
        "com.xiyu.bid.calendar.controller..",
        "com.xiyu.bid.collaboration.controller..",
        "com.xiyu.bid.competitionintel.controller..",
        "com.xiyu.bid.scoreanalysis.controller..",
        "com.xiyu.bid.roi.controller..",
        "com.xiyu.bid.versionhistory.controller..",
        "com.xiyu.bid.documenteditor.controller..",
        "com.xiyu.bid.documentexport.controller..",
        "com.xiyu.bid.documents.controller..",
        "com.xiyu.bid.settings.controller..",
        "com.xiyu.bid.fees.controller..",
        "com.xiyu.bid.projectworkflow.controller..",
        "com.xiyu.bid.resources.controller..",
        "com.xiyu.bid.casework.controller..",
        "com.xiyu.bid.analytics.controller.."
    };

    private static final String[] STRICT_SERVICE_PACKAGES = {
        "com.xiyu.bid.calendar.service..",
        "com.xiyu.bid.collaboration.service..",
        "com.xiyu.bid.competitionintel.service..",
        "com.xiyu.bid.scoreanalysis.service..",
        "com.xiyu.bid.roi.service..",
        "com.xiyu.bid.versionhistory.service..",
        "com.xiyu.bid.documenteditor.service..",
        "com.xiyu.bid.documentexport.service..",
        "com.xiyu.bid.historyproject.application..",
        "com.xiyu.bid.documents.service..",
        "com.xiyu.bid.settings.service..",
        "com.xiyu.bid.fees.service..",
        "com.xiyu.bid.projectworkflow.service..",
        "com.xiyu.bid.resources.service..",
        "com.xiyu.bid.casework.service..",
        "com.xiyu.bid.casework.application.service..",
        "com.xiyu.bid.analytics.service.."
    };

    private static final String[] STRICT_DTO_PACKAGES = {
        "com.xiyu.bid.calendar.dto..",
        "com.xiyu.bid.collaboration.dto..",
        "com.xiyu.bid.competitionintel.dto..",
        "com.xiyu.bid.scoreanalysis.dto..",
        "com.xiyu.bid.roi.dto..",
        "com.xiyu.bid.versionhistory.dto..",
        "com.xiyu.bid.documenteditor.dto..",
        "com.xiyu.bid.documentexport.dto..",
        "com.xiyu.bid.historyproject.dto..",
        "com.xiyu.bid.documents.dto..",
        "com.xiyu.bid.settings.dto..",
        "com.xiyu.bid.projectworkflow.dto..",
        "com.xiyu.bid.analytics.dto.."
    };

    private static final String[] DTO_ENTITY_FREE_PACKAGES = {
        "com.xiyu.bid.historyproject.dto..",
        "com.xiyu.bid.settings.dto..",
        "com.xiyu.bid.projectworkflow.dto..",
        "com.xiyu.bid.analytics.dto.."
    };

    private static final Set<String> ALLOWED_ROOT_CONTROLLERS = Set.of(
        "AdminProjectGroupController",
        "AdminRoleController",
        "AdminSettingsController",
        "AdminUserController",
        "AuthController",
        "TestController"
    );

    private static final Set<String> ALLOWED_ROOT_SERVICES = Set.of(
        "AdminUserService",
        "AuthService",
        "DataScopeConfigService",
        "EmailService",
        "EmailVerificationService",
        "PasswordResetService",
        "ProjectAccessScopeService",
        "ProjectGroupService",
        "RateLimitService",
        "RoleProfileService",
        "SessionService"
    );

    private static final Set<String> ALLOWED_ROOT_REPOSITORIES = Set.of(
        "AuditLogRepository",
        "CaseRepository",
        "EmailVerificationTokenRepository",
        "PasswordResetTokenRepository",
        "ProjectGroupRepository",
        "ProjectRepository",
        "QualificationRepository",
        "RefreshSessionRepository",
        "RoleProfileRepository",
        "TaskRepository",
        "TemplateDownloadRecordRepository",
        "TemplateRepository",
        "TemplateUseRecordRepository",
        "TemplateVersionRepository",
        "TenderRepository",
        "UserRepository"
    );

    private static final Set<String> ALLOWED_ROOT_ENTITIES = Set.of(
        "AuditLog",
        "Case",
        "EmailVerificationToken",
        "PasswordResetToken",
        "Project",
        "ProjectGroup",
        "Qualification",
        "RefreshSession",
        "RoleProfile",
        "RoleProfileCatalog",
        "Task",
        "Template",
        "TemplateDownloadRecord",
        "TemplateUseRecord",
        "TemplateVersion",
        "Tender",
        "TenderStatus",
        "User"
    );

    private static final Set<String> CYCLE_CHECK_EXCLUDED_SLICES = Set.of(
        "admin",
        "ai",
        "demo",
        "platform",
        "service",
        "settings",
        "batch",
        "changetracking",
        "mention",
        "notification",
        "casework",
        "config",
        "integration",
        "tender",
        "task",
        "project",
        "projectworkflow"
    );

    private static void assertOnlyWhitelistedRootPackageClasses(
        JavaClasses classes,
        String packageName,
        Set<String> allowedClasses,
        String layerLabel
    ) {
        Set<String> currentClasses = new TreeSet<>();
        classes.stream()
            .filter(javaClass -> javaClass.getPackageName().equals(packageName))
            .filter(javaClass -> !javaClass.getSimpleName().isBlank())
            .filter(javaClass -> !"package-info".equals(javaClass.getSimpleName()))
            .filter(javaClass -> !javaClass.getName().contains("$"))
            .forEach(javaClass -> currentClasses.add(javaClass.getSimpleName()));

        Set<String> unexpectedClasses = new TreeSet<>(currentClasses);
        unexpectedClasses.removeAll(allowedClasses);

        if (!unexpectedClasses.isEmpty()) {
            throw new AssertionError(
                "Root " + layerLabel + " package " + packageName
                    + " contains new business classes outside the allowlist: " + unexpectedClasses
                    + ". New business capability must live in a first-level module package instead of the root shared layer."
            );
        }
    }

    /**
     * RULE 1: Controller层不能直接依赖Repository层
     * 必须通过Service层进行数据访问
     * 当前覆盖新模块 + 已模块化成型的老模块
     */
    @ArchTest
    public static final ArchRule strict_module_controller_should_not_depend_on_repository =
        noClasses()
            .that().resideInAnyPackage(STRICT_CONTROLLER_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("纳入 ratchet 的模块 Controller 必须通过 Service 层访问数据");

    /**
     * RULE 1.1: Auth/Tender 控制器不得直接依赖 Repository
     * 作为分阶段整治的首批老模块约束
     */
    @ArchTest
    public static final ArchRule auth_tender_controller_should_not_depend_on_repository =
        noClasses()
            .that().resideInAPackage("com.xiyu.bid.controller..")
            .or().resideInAPackage("com.xiyu.bid.tender.controller..")
            .or().resideInAPackage("com.xiyu.bid.batch.controller..")
            .or().resideInAPackage("com.xiyu.bid.export.controller..")
            .or().resideInAPackage("com.xiyu.bid.bidresult.controller..")
            .or().resideInAPackage("com.xiyu.bid.approval.controller..")
            .should().dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("首批整治模块控制器必须通过Service层访问数据");

    /**
     * RULE 2: Service层不应依赖上层
     * Service层可以依赖Repository和其他Service的DTO
     * 当前覆盖新模块 + 已模块化成型的老模块
     */
    @ArchTest
    public static final ArchRule strict_module_service_should_not_depend_on_controller =
        noClasses()
            .that().resideInAnyPackage(STRICT_SERVICE_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("纳入 ratchet 的模块 Service 不应依赖 Controller");

    /**
     * RULE 3: Entity不能依赖Service/Controller
     * Entity必须是纯粹的领域模型
     * 所有模块都遵守
     */
    @ArchTest
    public static final ArchRule entities_should_be_independent =
        noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("Entity必须是纯粹的领域模型");

    /**
     * RULE 4: Controller不能依赖Entity
     * 仅覆盖已完成 DTO 收口的新模块 + 首批老模块
     */
    @ArchTest
    public static final ArchRule strict_module_controller_should_not_depend_on_entity =
        noClasses()
            .that().resideInAnyPackage(DTO_READY_CONTROLLER_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..entity..")
            .because("已完成 DTO 收口的模块 Controller 应通过 DTO 返回数据");

    /**
     * RULE 5: DTO不能依赖Service
     * 只检查新模块
     */
    @ArchTest
    public static final ArchRule new_module_dto_should_not_depend_on_service =
        noClasses()
            .that().resideInAnyPackage(STRICT_DTO_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .because("新模块DTO不应依赖Service");

    /**
     * RULE 5.1: 已纳入 DTO 收口的模块 DTO 不应依赖 Entity
     * 防止 Controller 虽然只返回 DTO，但接口契约仍透传实体枚举或实体类型
     */
    @ArchTest
    public static final ArchRule dto_ready_module_dto_should_not_depend_on_entity =
        noClasses()
            .that().resideInAnyPackage(DTO_ENTITY_FREE_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..entity..")
            .because("已完成 DTO 收口的模块 DTO 不应继续依赖 Entity 类型");

    /**
     * RULE 6: 禁止循环依赖
     * 所有模块都遵守
     */
    @ArchTest
    public static final void no_circular_dependencies(JavaClasses classes) {
        slices().matching("com.xiyu.bid.(*)..")
                .namingSlices("$1")
                .that(new DescribedPredicate<>("exclude legacy and cross-cutting support slices") {
                    @Override
                    public boolean test(Slice slice) {
                        return !CYCLE_CHECK_EXCLUDED_SLICES.contains(slice.getNamePart(1));
                    }
                })
                .should().beFreeOfCycles()
                .check(classes);
    }

    /**
     * RULE 7: 新模块之间应相互独立
     * 只检查新模块
     */
    @ArchTest
    public static final void new_modules_should_be_independent(JavaClasses classes) {
        slices().matching("com.xiyu.bid.(calendar|collaboration|competitionintel|scoreanalysis|roi|versionhistory|documenteditor|documents)..")
                .should().notDependOnEachOther()
                .check(classes);
    }

    /**
     * RULE 7.1: documentexport 只能通过 historyproject.application / dto 访问历史项目快照能力
     * 禁止跳过应用边界直连其 repository/entity
     */
    @ArchTest
    public static final ArchRule documentexport_should_only_depend_on_historyproject_api =
        noClasses()
            .that().resideInAnyPackage(
                "com.xiyu.bid.documentexport.service..",
                "com.xiyu.bid.documentexport.controller.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.xiyu.bid.historyproject.repository..",
                "com.xiyu.bid.historyproject.entity.."
            )
            .because("documentexport 应通过 historyproject 的应用边界交互，避免跨模块直连持久化实现");

    /**
     * RULE 7.2: historyproject 不应反向耦合到 casework/documenteditor/documentexport 内部实现
     * 保持历史项目快照模块可被多个上游消费
     */
    @ArchTest
    public static final ArchRule historyproject_should_not_depend_on_casework_or_document_internals =
        noClasses()
            .that().resideInAnyPackage(
                "com.xiyu.bid.historyproject.application..",
                "com.xiyu.bid.historyproject.dto..",
                "com.xiyu.bid.historyproject.entity..",
                "com.xiyu.bid.historyproject.repository.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.xiyu.bid.casework..",
                "com.xiyu.bid.documenteditor..",
                "com.xiyu.bid.documentexport.."
            )
            .because("historyproject 是沉淀边界，不应回看案例或文档模块内部实现");

    /**
     * RULE 7.3: documenteditor 不得依赖历史项目/案例/导出模块
     * 编辑边界保持聚焦在文档结构与编辑流程本身
     */
    @ArchTest
    public static final ArchRule documenteditor_should_not_depend_on_archive_or_case_modules =
        noClasses()
            .that().resideInAnyPackage(
                "com.xiyu.bid.documenteditor.service..",
                "com.xiyu.bid.documenteditor.controller..",
                "com.xiyu.bid.documenteditor.dto..",
                "com.xiyu.bid.documenteditor.entity..",
                "com.xiyu.bid.documenteditor.repository.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.xiyu.bid.historyproject..",
                "com.xiyu.bid.casework..",
                "com.xiyu.bid.documentexport.."
            )
            .because("documenteditor 是上游编辑模块，不应直接承接归档沉淀或案例资产职责");

    /**
     * RULE 8: Util工具类不能依赖业务逻辑
     * 所有模块都遵守
     */
    @ArchTest
    public static final ArchRule utils_should_not_depend_on_business_logic =
        noClasses()
            .that().haveSimpleNameContaining("Util")
            .or().haveSimpleNameContaining("Helper")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("工具类应该是无状态的纯函数");

    /**
     * RULE 9: Config配置类不能依赖Service
     * 所有模块都遵守
     */
    @ArchTest
    public static final ArchRule config_should_not_depend_on_service =
        noClasses()
            .that().resideInAPackage("..config..")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .because("配置类不应依赖业务逻辑层");

    /**
     * RULE 10: 禁止在Controller中直接使用JPA EntityManager
     * 只检查新模块
     */
    @ArchTest
    public static final ArchRule new_module_controller_should_not_use_entity_manager =
        noClasses()
            .that().resideInAPackage("com.xiyu.bid.calendar.controller..")
            .or().resideInAPackage("com.xiyu.bid.collaboration.controller..")
            .or().resideInAPackage("com.xiyu.bid.competitionintel.controller..")
            .or().resideInAPackage("com.xiyu.bid.scoreanalysis.controller..")
            .or().resideInAPackage("com.xiyu.bid.roi.controller..")
            .or().resideInAPackage("com.xiyu.bid.versionhistory.controller..")
            .or().resideInAPackage("com.xiyu.bid.documenteditor.controller..")
            .or().resideInAPackage("com.xiyu.bid.documentexport.controller..")
            .or().resideInAPackage("com.xiyu.bid.documents.controller..")
            .should().dependOnClassesThat()
            .haveSimpleNameContaining("EntityManager")
            .orShould().dependOnClassesThat()
            .haveSimpleNameContaining("SessionFactory")
            .because("新模块Controller必须通过Repository访问数据库");

    @ArchTest
    public static final void root_controller_package_should_only_contain_whitelisted_classes(JavaClasses classes) {
        assertOnlyWhitelistedRootPackageClasses(
            classes,
            "com.xiyu.bid.controller",
            ALLOWED_ROOT_CONTROLLERS,
            "controller"
        );
    }

    @ArchTest
    public static final void root_service_package_should_only_contain_whitelisted_classes(JavaClasses classes) {
        assertOnlyWhitelistedRootPackageClasses(
            classes,
            "com.xiyu.bid.service",
            ALLOWED_ROOT_SERVICES,
            "service"
        );
    }

    @ArchTest
    public static final void root_repository_package_should_only_contain_whitelisted_classes(JavaClasses classes) {
        assertOnlyWhitelistedRootPackageClasses(
            classes,
            "com.xiyu.bid.repository",
            ALLOWED_ROOT_REPOSITORIES,
            "repository"
        );
    }

    @ArchTest
    public static final void root_entity_package_should_only_contain_whitelisted_classes(JavaClasses classes) {
        assertOnlyWhitelistedRootPackageClasses(
            classes,
            "com.xiyu.bid.entity",
            ALLOWED_ROOT_ENTITIES,
            "entity"
        );
    }

    /**
     * RULE 11: JPA Entity 禁止放置在 domain/application/controller/service/repository 包中
     * Entity 必须位于含 "entity" 或 "persistence" 的包路径
     * 补足了 RULE 3 仅检查"Entity 不能依赖谁"但未检查"Entity 放哪"的盲区
     *
     * 漏洞背景：WebhookDeliveryLog.java 放在 webhook/domain/ 通过了所有已有规则
     * 因为 RULE 3 只禁止 entity 依赖 service/controller，RULE 9 只禁止 config 依赖 service，
     * 均未约束 entity 自身的放置位置。新规则确保 entity 必须在专门的持久化包下。
     *
     * 白名单路径（允许放置 Entity 的包）：
     *   ..entity..              — 标准 entity 层，如 com.xiyu.bid.entity.Xxx
     *   ..infrastructure.entity.. — 模块级 entity 层，如 com.xiyu.bid.calendar.entity.Xxx
     *   ..persistence.entity..   — 深度分层 entity，如 com.xiyu.bid.xxx.infrastructure.persistence.entity.Xxx
     */
    private static final String LEGACY_ENTITY_SIMPLE_NAME = "CrmProjectMapping";

    /**
     * 自定义 ArchCondition：禁止 Entity 位于受限包（排除历史遗留）
     */
    private static final ArchCondition<JavaClass> NO_ENTITY_IN_RESTRICTED_PKGS = new ArchCondition<JavaClass>(
        "no JPA Entity in restricted packages (domain/application/controller/service/repository)"
    ) {
        private final Set<String> RESTRICTED = Set.of(
            "domain", "application", "controller", "service", "repository"
        );
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            if (item.getSimpleName().equals(LEGACY_ENTITY_SIMPLE_NAME)) return;
            for (String pkg : item.getPackageName().split("\\.")) {
                if (RESTRICTED.contains(pkg)) {
                    events.add(SimpleConditionEvent.violated(item,
                        "JPA Entity " + item.getSimpleName() + " resides in restricted package '" + pkg
                        + "'. Move to ..entity.. or ..persistence.entity.. package."));
                }
            }
        }
    };

    /**
     * RULE 11: JPA Entity 禁止放置在 domain/application/controller/service/repository 包中
     * Entity 必须位于含 "entity" 或 "persistence.entity" 的包路径
     * 补足了 RULE 3 仅检查"Entity 不能依赖谁"但未检查"Entity 放哪"的盲区
     *
     * 漏洞背景：WebhookDeliveryLog.java 放在 webhook/domain/ 通过了所有已有规则
     * 因为 RULE 3 只禁止 entity 依赖 service/controller，RULE 9 只禁止 config 依赖 service，
     * 均未约束 entity 自身的放置位置。新规则确保 entity 必须在专门的持久化包下。
     *
     * 豁免：CrmProjectMapping — PR #378 引入的历史遗留，待后续重构迁移至 crm.infrastructure.entity 包
     */
    @ArchTest
    public static final ArchRule jpa_entities_forbidden_in_non_persistence_packages =
        classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .or().areAnnotatedWith("jakarta.persistence.MappedSuperclass")
            .should(NO_ENTITY_IN_RESTRICTED_PKGS)
            .because(
                "JPA Entity (@Entity/@MappedSuperclass) 禁止放置在 domain/application/controller/service/repository 包中。"
                    + " Entity 必须位于含 'entity' 或 'persistence.entity' 的包路径。"
                    + " 正确路径: com.xiyu.bid.entity.Xxx / com.xiyu.bid.xxx.entity.Xxx / com.xiyu.bid.xxx.infrastructure.persistence.entity.Xxx"
                    + " CrmProjectMapping 暂豁免（历史遗留，待重构）。"
            );
}
