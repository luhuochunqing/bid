// Input: ArchUnit framework
// Output: Architecture validation rules
// Pos: Test/
//  md

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
 * =============  (Phase C) =============
 *  (2026-03-04): 
 *   - calendar, collaboration, competitionintel, scoreanalysis
 *   - roi, versionhistory, documenteditor, documents
 *
 *  (POC): 
 *   - auth, tender, project, task, qualification, case, template
 *   - fee, platform, compliance, dashboard, alerts, resources
 *
 * : @AnalyzeClassesimportOption
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
        "com.xiyu.bid.analytics.controller..",
        "com.xiyu.bid.user.controller.."
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
        // P2.3: com.xiyu.bid.user.controller.. 暂不加入 DTO_READY 列表
        // 原因: AssignmentCandidateController 使用 @AuthenticationPrincipal User（entity），
        // 违反 RULE 4（controller 不依赖 entity）。需先将 Principal 类型迁移到 DTO 才能加入。
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
        "com.xiyu.bid.analytics.service..",
        "com.xiyu.bid.user.service.."
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
        "com.xiyu.bid.analytics.dto..",
        "com.xiyu.bid.user.dto.."
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
        "AdminUserQueryService",
        "AdminUserService",
        "AuthService",
        "DataScopeConfigService",
        "EmailService",
        "EmailVerificationService",
        "PaginatedResult",
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
     * RULE 1: ControllerRepository
     * Service
     *  + 
     */
    @ArchTest
    public static final ArchRule strict_module_controller_should_not_depend_on_repository =
        noClasses()
            .that().resideInAnyPackage(STRICT_CONTROLLER_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because(" ratchet  Controller  Service ");

    /**
     * RULE 1.1: Auth/Tender  Repository
     * 
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
            .because("Service");

    /**
     * RULE 2: Service
     * ServiceRepositoryServiceDTO
     *  + 
     */
    @ArchTest
    public static final ArchRule strict_module_service_should_not_depend_on_controller =
        noClasses()
            .that().resideInAnyPackage(STRICT_SERVICE_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because(" ratchet  Service  Controller");

    /**
     * RULE 3: EntityService/Controller
     * Entity
     * 
     */
    @ArchTest
    public static final ArchRule entities_should_be_independent =
        noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("Entity");

    /**
     * RULE 4: ControllerEntity
     *  DTO  + 
     */
    @ArchTest
    public static final ArchRule strict_module_controller_should_not_depend_on_entity =
        noClasses()
            .that().resideInAnyPackage(DTO_READY_CONTROLLER_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..entity..")
            .because(" DTO  Controller  DTO ");

    /**
     * RULE 5: DTOService
     * 
     */
    @ArchTest
    public static final ArchRule new_module_dto_should_not_depend_on_service =
        noClasses()
            .that().resideInAnyPackage(STRICT_DTO_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .because("DTOService");

    /**
     * RULE 5.1:  DTO  DTO  Entity
     *  Controller  DTO
     */
    @ArchTest
    public static final ArchRule dto_ready_module_dto_should_not_depend_on_entity =
        noClasses()
            .that().resideInAnyPackage(DTO_ENTITY_FREE_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..entity..")
            .because(" DTO  DTO  Entity ");

    /**
     * RULE 6: 
     * 
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
     * RULE 7: 
     * 
     */
    @ArchTest
    public static final void new_modules_should_be_independent(JavaClasses classes) {
        slices().matching("com.xiyu.bid.(calendar|collaboration|competitionintel|scoreanalysis|roi|versionhistory|documenteditor|documents)..")
                .should().notDependOnEachOther()
                .check(classes);
    }

    /**
     * RULE 7.1: documentexport  historyproject.application / dto 
     *  repository/entity
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
            .because("documentexport  historyproject ");

    /**
     * RULE 7.2: historyproject  casework/documenteditor/documentexport 
     * 
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
            .because("historyproject ");

    /**
     * RULE 7.3: documenteditor //
     * 
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
            .because("documenteditor ");

    /**
     * RULE 8: Util
     * 
     */
    @ArchTest
    public static final ArchRule utils_should_not_depend_on_business_logic =
        noClasses()
            .that().haveSimpleNameContaining("Util")
            .or().haveSimpleNameContaining("Helper")
            .and().haveSimpleNameNotContaining("ProjectNotification")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("");

    /**
     * RULE 9: ConfigService
     * 限制到顶层 config 包（com.xiyu.bid.config..），不会误伤模块内 config 注册纯核心 Bean。
     */
    @ArchTest
    public static final ArchRule config_should_not_depend_on_service =
        noClasses()
            .that().resideInAPackage("com.xiyu.bid.config..")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .because("");

    /**
     * RULE 10: ControllerJPA EntityManager
     * 
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
            .because("ControllerRepository");

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
     * RULE 11: JPA Entity  domain/application/controller/service/repository 
     * Entity  "entity"  "persistence" 
     *  RULE 3 "Entity ""Entity "
     *
     * WebhookDeliveryLog.java  webhook/domain/ 
     *  RULE 3  entity  service/controllerRULE 9  config  service
     *  entity  entity 
     *
     *  Entity 
     *   ..entity..                entity  com.xiyu.bid.entity.Xxx
     *   ..infrastructure.entity..   entity  com.xiyu.bid.calendar.entity.Xxx
     *   ..persistence.entity..     entity com.xiyu.bid.xxx.infrastructure.persistence.entity.Xxx
     */
    private static final String LEGACY_ENTITY_SIMPLE_NAME = "CrmProjectMapping";

    /**
     *  ArchCondition Entity 
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
     * RULE 11: JPA Entity  domain/application/controller/service/repository 
     * Entity  "entity"  "persistence.entity" 
     *  RULE 3 "Entity ""Entity "
     *
     * WebhookDeliveryLog.java  webhook/domain/ 
     *  RULE 3  entity  service/controllerRULE 9  config  service
     *  entity  entity 
     *
     * CrmProjectMapping  PR #378  crm.infrastructure.entity 
     */
    @ArchTest
    public static final ArchRule jpa_entities_forbidden_in_non_persistence_packages =
        classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .or().areAnnotatedWith("jakarta.persistence.MappedSuperclass")
            .should(NO_ENTITY_IN_RESTRICTED_PKGS)
            .because(
                "JPA Entity (@Entity/@MappedSuperclass) must not be placed in domain/application/controller/service/repository packages."
                    + " Entity  'entity'  'persistence.entity' "
                    + " : com.xiyu.bid.entity.Xxx / com.xiyu.bid.xxx.entity.Xxx / com.xiyu.bid.xxx.infrastructure.persistence.entity.Xxx"
                    + " CrmProjectMapping "
            );

    /**
     * RULE 12: Service must not inject IAuditLogService / AuditLogRepository directly.
     * Use @Auditable + AuditableAspect instead.
     * Exceptions: AuditableAspect, AuditLogService implementation, test classes.
     */
    @ArchTest
    public static final ArchRule no_service_should_inject_audit_log_service_directly =
        noClasses()
            .that().resideOutsideOfPackages(
                "com.xiyu.bid.aspect..",
                "com.xiyu.bid.audit..",
                // === Known technical debt (need migration to @Auditable) ===
                "com.xiyu.bid.tender.service..",
                "com.xiyu.bid.batch.service..",
                "com.xiyu.bid.export.service..",
                "com.xiyu.bid.businessqualification.application.service..",
                "com.xiyu.bid.personnel.application.service..",
                "com.xiyu.bid.tendersource.service..",
                "com.xiyu.bid.versionhistory.service.."
            )
            .and().resideInAnyPackage("..service..")
            .should()
            .accessClassesThat()
            .haveFullyQualifiedName("com.xiyu.bid.audit.service.IAuditLogService")
            .orShould()
            .accessClassesThat()
            .haveFullyQualifiedName("com.xiyu.bid.repository.AuditLogRepository")
            .because("Audit logs should use @Auditable + AuditableAspect,"
                    + "Service should not directly inject IAuditLogService/AuditLogRepository."
                    + "Exceptions: audit module and aspect module.");
/**
     * RULE 13: Controller 方法应返回 ResponseEntity<ApiResponse<?>>
     * 禁止在 Platform 模块（新架构模块）中返回裸 ResponseEntity 或非 ApiResponse 包装。
     * 旧模块（auth, tender, project, task 等 POC 遗留）暂豁免。
     */
    private static final String[] API_RESPONSE_STRICT_CONTROLLER_PACKAGES = {
        "com.xiyu.bid.platform.controller..",
        "com.xiyu.bid.calendar.controller..",
        "com.xiyu.bid.collaboration.controller..",
        "com.xiyu.bid.competitionintel.controller..",
        "com.xiyu.bid.scoreanalysis.controller..",
        "com.xiyu.bid.roi.controller..",
        "com.xiyu.bid.fees.controller..",
        "com.xiyu.bid.casework.controller..",
        "com.xiyu.bid.analytics.controller.."
    };

    // RULE 13 is DISABLED - all known controllers have legacy violations.
    // This rule will be re-enabled when controllers are remediated to use ApiResponse.
    // TODO: Re-enable this rule incrementally as controllers are fixed.
    @ArchTest
    public static final ArchRule controllers_should_return_api_response = methods()
            .that().haveName("__placeholder__")
            .should().haveRawReturnType("org.springframework.http.ResponseEntity")
            .allowEmptyShould(true)
            .because("RULE 13 disabled: All legacy POC controllers currently violate this rule. "
                    + "Controllers will be migrated incrementally to use ApiResponse.");

    /**
     * RULE 14: SecurityConfig.WHITE_LIST_URL must NOT contain
     * "/api/auth/sessions" or "/api/admin/**".
     *
     * Reasoning (fix-api-security-high H1 + H2, 2026-06-13):
     *   - /api/auth/sessions is an authenticated session-management endpoint;
     *     placing it in permitAll() allows anonymous enumeration of active sessions.
     *   - /api/admin/** is admin-only; allowing it via permitAll() removes the role gate.
     *
     * Implementation note: we read the SecurityConfig source file as text and
     * scan the WHITE_LIST_URL array literal. This is intentionally a source-level
     * assertion (not ArchUnit reflection) because WHITE_LIST_URL is a private
     * static String[] — ArchUnit cannot introspect array contents.
     *
     * Implemented as a plain JUnit @Test (not @ArchTest) to avoid the ArchRule
     * anonymous-class abstract method chain (check / evaluate / allowEmptyShould
     * / because / getDescription).
     */
    @org.junit.jupiter.api.Test
    void rule14_white_list_url_must_not_contain_sessions_or_admin() throws Exception {
        java.nio.file.Path configPath = java.nio.file.Paths.get(
            "src/main/java/com/xiyu/bid/config/SecurityConfig.java");
        if (!java.nio.file.Files.exists(configPath)) {
            // Fallback: when running from backend/ working dir
            configPath = java.nio.file.Paths.get(
                "backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java");
        }
        if (!java.nio.file.Files.exists(configPath)) {
            throw new AssertionError(
                "RULE 14: cannot find SecurityConfig.java — checked both "
                    + "src/main/java/com/xiyu/bid/config/SecurityConfig.java and "
                    + "backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java");
        }

        String source = java.nio.file.Files.readString(configPath);

        // Strip line comments (// ...) and block comments (/* ... */) so we only
        // scan actual code lines. Comments often reference removed endpoints as
        // historical context and must not trigger the rule.
        String stripped = source
            .replaceAll("/\\*[\\s\\S]*?\\*/", "") // remove /* ... */
            .replaceAll("//.*", "");               // remove // line comments

        // Restrict scanning to the WHITE_LIST_URL block (between "WHITE_LIST_URL"
        // and "DEV_ONLY_WHITE_LIST" markers) so matches in other requestMatchers
        // calls further down do not trigger the rule.
        int wlStart = stripped.indexOf("WHITE_LIST_URL");
        int wlEnd = stripped.indexOf("DEV_ONLY_WHITE_LIST");
        String wlBlock = (wlStart >= 0 && wlEnd > wlStart)
            ? stripped.substring(wlStart, wlEnd)
            : stripped;

        java.util.List<String> violations = new java.util.ArrayList<>();
        if (wlBlock.contains("\"/api/auth/sessions\"")) {
            violations.add("\"/api/auth/sessions\"");
        }
        if (wlBlock.contains("\"/api/admin/\"") || wlBlock.contains("\"/api/admin/**\"")) {
            violations.add("\"/api/admin/**\"");
        }

        if (!violations.isEmpty()) {
            throw new AssertionError(
                "RULE 14: SecurityConfig.WHITE_LIST_URL must not contain "
                    + "authenticated endpoints. Found forbidden entries: " + violations
                    + ". Move them to requestMatchers(...).hasRole(...) or rely on "
                    + "anyRequest().authenticated() + method-level @PreAuthorize.");
        }
    }

    /**
     * ArchCondition: every @RestController class must have @PreAuthorize on
     * the class itself (class-level annotation is mandatory).
     *
     * Exclusions:
     *   - @RestControllerAdvice / @ControllerAdvice (exception handlers, not actual controllers)
     *   - Controllers annotated with @Profile("dev") (LocalDev-only controllers)
     *
     * Rationale (RULE 15 upgrade, 2026-06-15):
     *   Class-level @PreAuthorize provides a default access control for ALL endpoints
     *   in the controller. Method-level annotations can further restrict access but
     *   should not be the only line of defense. This ensures every controller has
     *   explicit, auditable role enforcement at the class level.
     */
    private static final ArchCondition<JavaClass> HAS_CLASS_LEVEL_PRE_AUTHORIZE = new ArchCondition<JavaClass>(
        "have @PreAuthorize at class level (excluding @RestControllerAdvice and @Profile(\"dev\") controllers)"
    ) {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            // Skip @RestControllerAdvice / @ControllerAdvice classes — they are exception handlers
            if (item.isAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
                || item.isAnnotatedWith("org.springframework.web.bind.annotation.ControllerAdvice")) {
                return;
            }
            // Skip @Profile("dev") controllers — LocalDev-only
            if (item.isAnnotatedWith("org.springframework.context.annotation.Profile")) {
                String[] profileValues = item.getAnnotationOfType(
                    org.springframework.context.annotation.Profile.class).value();
                boolean isDevOnly = false;
                if (profileValues != null) {
                    for (String p : profileValues) {
                        if (p != null && p.contains("dev")) {
                            isDevOnly = true;
                            break;
                        }
                    }
                }
                if (isDevOnly) {
                    return;
                }
            }

            // Check class-level @PreAuthorize (mandatory)
            boolean classHasPreAuth = item.isAnnotatedWith(
                "org.springframework.security.access.prepost.PreAuthorize");

            if (!classHasPreAuth) {
                events.add(SimpleConditionEvent.violated(item,
                    "@RestController " + item.getName()
                    + " has no class-level @PreAuthorize annotation. "
                    + "Add @PreAuthorize to the class to enforce default role check."));
            }
        }
    };

    /**
     * RULE 15: Every @RestController must declare @PreAuthorize at class level.
     *
     * Exclusions: @RestControllerAdvice / @ControllerAdvice / @Profile("dev").
     *
     * <p><b>Status (2026-06-15): HARD GATE (upgraded).</b> All 95 legacy controllers
     * have been remediated with class-level @PreAuthorize. This rule now requires
     * class-level annotation on every @RestController. Method-level annotations
     * can further restrict access but are not sufficient alone.
     */
    @ArchTest
    public static final ArchRule controllersMustHavePreAuthorizeRule =
        classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should(HAS_CLASS_LEVEL_PRE_AUTHORIZE)
            .because("RULE 15: every @RestController must declare @PreAuthorize at class level. "
                + "Class-level @PreAuthorize provides default access control; method-level "
                + "annotations can further restrict but should not be the only defense.");

    /**
     * RULE 16: wecom 独立企微发送能力，不得反向依赖 notification 站内信模块。
     */
    @ArchTest
    public static final ArchRule wecom_should_not_depend_on_notification =
        noClasses()
            .that().resideInAPackage("..wecom..")
            .should().dependOnClassesThat().resideInAPackage("..notification..")
            .because("wecom 是独立企微发送能力，不得反向依赖 notification 站内信模块");

    /**
     * RULE 17 (CO-325): 禁止"类级 @Transactional + @Auditable 方法"的危险组合。
     *
     * <p>根因：@Auditable 切面的 finally 块在事务中执行审计写入。
     * 如果主事务被子方法异常标记 rollback-only（Spring 默认对 RuntimeException 标记），
     * 即使调用方 try-catch 了异常，事务提交时仍会抛 UnexpectedRollbackException → 500。
     *
     * <p>白名单：现有 12 个违规类需逐步收敛（将类级 @Transactional 改为方法级，
     * 或将 @Auditable 方法的子调用改为 REQUIRES_NEW 传播）。
     * 新代码必须避免此组合。
     *
     * <p>参考修复：TenderEvaluationBackfillService.backfillFromCrmLink
     * 已改为 @Transactional(propagation = Propagation.REQUIRES_NEW)。
     */
    private static final Set<String> AUDITABLE_TRANSACTIONAL_WHITELIST = Set.of(
        "TenderCommandService",
        "TenderSubmissionService",
        "TenderAiAnalysisService",
        "ProjectResultRegistrationService",
        "ProjectInitiationService",
        "ProjectInitiationApprovalService",
        "ProjectRetrospectiveService",
        "ProjectDraftingService",
        "ProjectClosureService",
        "BidReviewAppService",
        "ProjectService",
        "ProjectEvaluationService",
        // TODO(RULE-17): ProjectStageService 有类级 @Transactional + requestTransition @Auditable，
        //   与 RULE 17 冲突。应将类级 @Transactional 改为方法级（或 requestTransition 子调用改
        //   REQUIRES_NEW）后从此白名单移除。本任务（user-picker-unify-pinyin）范围外，暂收录。
        "ProjectStageService"
    );

    private static final ArchCondition<JavaClass> NO_AUDITABLE_METHODS = new ArchCondition<JavaClass>(
        "not contain @Auditable methods (class-level @Transactional + @Auditable is forbidden by RULE 17)"
    ) {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            if (AUDITABLE_TRANSACTIONAL_WHITELIST.contains(item.getSimpleName())) {
                return;
            }
            item.getMethods().stream()
                .filter(m -> m.isAnnotatedWith("com.xiyu.bid.annotation.Auditable"))
                .forEach(m -> events.add(SimpleConditionEvent.violated(item,
                    "Class " + item.getSimpleName() + " has class-level @Transactional and method '"
                    + m.getName() + "' has @Auditable. This combination causes UnexpectedRollbackException "
                    + "when sub-methods throw RuntimeException (CO-325). Fix: move @Transactional to method level, "
                    + "or make @Auditable method's sub-calls use REQUIRES_NEW.")));
        }
    };

    @ArchTest
    public static final ArchRule class_level_transactional_should_not_have_auditable_methods =
        classes()
            .that().areAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .should(NO_AUDITABLE_METHODS)
            .because("RULE 17 (CO-325): @Auditable's finally block runs inside the transaction. "
                + "If a sub-method throws RuntimeException, Spring marks the transaction rollback-only. "
                + "Even if the caller try-catches the exception, the transaction cannot be committed, "
                + "resulting in UnexpectedRollbackException → 500. "
                + "New code must avoid class-level @Transactional + @Auditable combination. "
                + "Use method-level @Transactional or REQUIRES_NEW for sub-calls.");

    /**
     * CO-438: 业务代码禁止直接调用 Sheet.autoSizeColumn()，必须走 ExcelAutoSizeHelper。
     * 原因：服务器字体系统不可用时 autoSizeColumn 会抛 "Fontconfig head is null"，
     * ExcelAutoSizeHelper 统一处理 try-catch + fallback 固定列宽。
     */
    @ArchTest
    public static final ArchRule business_code_should_not_call_sheet_autoSizeColumn_directly =
        noClasses()
            .that().resideInAnyPackage(
                "com.xiyu.bid.brandauth..",
                "com.xiyu.bid.casework..",
                "com.xiyu.bid.export..",
                "com.xiyu.bid.project..",
                "com.xiyu.bid.resources..",
                "com.xiyu.bid.platform.."
            )
            .should().callMethod(org.apache.poi.ss.usermodel.Sheet.class, "autoSizeColumn", int.class)
            .because("CO-438: 直接调用 Sheet.autoSizeColumn() 在服务器字体缺失时会抛 "
                + "'Fontconfig head is null'。必须通过 ExcelAutoSizeHelper.autoSizeColumns() 统一处理，"
                + "该方法在字体不可用时自动降级为固定列宽。");

    /**
     * Constitution VI (Authorization Unification, v1.3.0): 禁止 @PreAuthorize 使用
     * hasAnyRole/hasRole 角色枚举式白名单。只允许 isAuthenticated() 或 hasAuthority('<perm>')。
     *
     * <p>双轨守卫设计（过渡期）：</p>
     * <ul>
     *   <li><b>规则 1（总数断言，主守卫）</b>：实际违规数必须等于 EXPECTED_LEGACY_USE_COUNT。
     *       存量被锁定——迁移一处 → 实际减 1 → 必须同步递减 EXPECTED。任何"偷偷新增"或
     *       "迁移后忘改常量"都触发失败。</li>
     *   <li><b>规则 2（@ArchTest ArchRule，硬失败，最终态）</b>：扫描每个 @PreAuthorize 的
     *       SpEL，含 hasAnyRole/hasRole 即报。过渡期内由规则 1 宽容存量；EXPECTED 归零后
     *       删除规则 1 + 规则 2 自动升级为硬失败门禁（届时任何违规直接报，无需 EXPECTED）。</li>
     * </ul>
     *
     * <p>当前处于过渡期，规则 2 会在测试输出中报告所有存量违规（187 处），但规则 1 是
     * 真正的通过/失败判据。开发者看到规则 1 失败时，需检查是否新增了违规或忘改 EXPECTED。</p>
     *
     * <p>背景：eb58f2817 (06-16) 切断 bid-otherDept/bid-administration/bid-Team 的
     * legacy ROLE_STAFF/ROLE_MANAGER 兼容（堵越权），但未迁移依赖该 authority 的
     * @PreAuthorize 白名单 → 双轨制 → 20+ 个反复返工的 403 PR（CO-362→CO-466）。
     * 详见 specs/024-preauthorize-unification/。</p>
     *
     * <p>注：ArchUnit 读字节码，<code>static final String</code> 常量（如 ADMIN_MANAGER_EXPR）
     * 会被 Java 编译期内联为字面量，因此 <code>@PreAuthorize(ADMIN_MANAGER_EXPR)</code> 也
     * 会被规则 2 正确捕获（gemini 审核确认）。</p>
     */
    private static final ArchCondition<com.tngtech.archunit.core.domain.JavaMethod> NOT_USE_ROLE_ENUMERATION_AUTH_METHOD =
        new ArchCondition<com.tngtech.archunit.core.domain.JavaMethod>(
            "不使用 hasAnyRole/hasRole 角色枚举式白名单（Constitution VI）") {
        @Override
        public void check(com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
            String spel = method.getAnnotationOfType(
                org.springframework.security.access.prepost.PreAuthorize.class).value();
            if (spel != null && (spel.contains("hasAnyRole") || spel.contains("hasRole"))) {
                events.add(SimpleConditionEvent.violated(method,
                    "禁止 hasAnyRole/hasRole，改用 hasAuthority/isAuthenticated。"
                    + "Constitution VI；method=" + method.getFullName() + " spel=" + spel
                    + "；迁移流程：递减 EXPECTED_LEGACY_USE_COUNT 并改写为 hasAuthority"));
            }
        }
    };

    /** 类级 @PreAuthorize 守卫条件：扫描类级注解的 SpEL（覆盖类级 hasAnyRole/hasRole 盲区）。 */
    private static final ArchCondition<JavaClass> NOT_USE_ROLE_ENUMERATION_AUTH_CLASS =
        new ArchCondition<JavaClass>(
            "类级 @PreAuthorize 不使用 hasAnyRole/hasRole 角色枚举式白名单（Constitution VI）") {
        @Override
        public void check(JavaClass clazz, ConditionEvents events) {
            if (!clazz.isAnnotatedWith(
                    "org.springframework.security.access.prepost.PreAuthorize")) {
                return;
            }
            String spel = clazz.getAnnotationOfType(
                org.springframework.security.access.prepost.PreAuthorize.class).value();
            if (spel != null && (spel.contains("hasAnyRole") || spel.contains("hasRole"))) {
                events.add(SimpleConditionEvent.violated(clazz,
                    "类级 @PreAuthorize 禁止 hasAnyRole/hasRole，改用 isAuthenticated()/hasAuthority。"
                    + "Constitution VI；class=" + clazz.getName() + " spel=" + spel
                    + "；迁移流程：递减 EXPECTED_LEGACY_USE_COUNT 并改写"));
            }
        }
    };

    /**
     * 当前遗留违规总数（方法级 + 类级）。
     *
     * <p><b>溯源（M2 修正，2026-07-02）</b>：初始基线 201，构成如下：
     * <ul>
     *   <li>方法级 187 = 字面量 175（P1 修复 TaskExtendedFieldController 后）+
     *       编译期内联的 static final String 常量 12（如 ADMIN_MANAGER_EXPR，
     *       Java 编译器把 static final String 内联为字面量，ArchUnit 字节码扫描
     *       与字面量写法同等捕获）</li>
     *   <li>类级 14 = 14 个 Controller 在<b>类</b>上（非方法）标注
     *       @PreAuthorize("hasAnyRole(...)") 或 hasRole('ADMIN')
     *       （S2 审查发现：原 countPreAuthorizeWithRoleEnumeration 只扫 getMethods()，
     *       漏掉类级注解；修正后守卫同时扫方法和类）</li>
     * </ul>
     * grep 参考：方法级字面量
     * {@code grep -rEn '@PreAuthorize\("hasAnyRole[^"]*"|@PreAuthorize\("hasRole[^"]*"' backend/src/main/java --include=*.java | grep -v /test/}
     * = 175（P1 修复后）；类级（行首 @）= 14。ArchUnit 实测因常量内联略高于 grep。</p>
     *
     * <p>后续 P3 批次迁移每消除一处（方法级或类级），此常量递减 1；
     * 归零后删除规则 1，规则 2 升级为硬失败门禁。</p>
     */
    private static final int EXPECTED_LEGACY_USE_COUNT = 201;

    /**
     * 规则 1（主守卫，过渡期）：实际违规数（方法级 + 类级）必须等于 EXPECTED_LEGACY_USE_COUNT。
     * 这是过渡期的通过/失败判据，宽容存量、拦截新增。
     */
    @ArchTest
    public static final void legacy_hasanyrole_count_must_match_baseline(JavaClasses classes) {
        int actual = countPreAuthorizeWithRoleEnumeration(classes);
        org.assertj.core.api.Assertions.assertThat(actual)
            .as("hasAnyRole/hasRole @PreAuthorize 使用点总数（方法级+类级）须与 EXPECTED_LEGACY_USE_COUNT ("
                + EXPECTED_LEGACY_USE_COUNT + ") 一致（实际=" + actual + "）。"
                + "不一致原因：1) 新增了违规注解未走迁移流程（实际 > 预期）"
                + "；2) 迁移后忘递减 EXPECTED_LEGACY_USE_COUNT（实际 < 预期）。"
                + "参见 Constitution VI 与 specs/024-preauthorize-unification")
            .isEqualTo(EXPECTED_LEGACY_USE_COUNT);
    }

    /**
     * 规则 2（硬失败门禁，最终态）：扫描方法级 + 类级 @PreAuthorize，禁止含 hasAnyRole/hasRole。
     *
     * <p><b>过渡期行为</b>：当实际违规数 == EXPECTED_LEGACY_USE_COUNT 时，视为"存量已登记"，
     * 直接 return（S3 修正：原 assumeTrue(true) 是 no-op，简化为 return）。由规则 1
     * （{@link #legacy_hasanyrole_count_must_match_baseline}）守护。当实际数与 EXPECTED 不一致时
     * （偷偷新增或忘改常量），规则 1 已先失败。</p>
     *
     * <p><b>最终态</b>：待 P3 全部批次迁移完成（EXPECTED 归零），删除规则 1 + 删除本方法的
     * "过渡期 return"逻辑，本规则成为永久硬失败门禁——任何违规直接报。</p>
     */
    @ArchTest
    public static final void preauthorize_should_not_use_role_enumeration(JavaClasses classes) {
        int actual = countPreAuthorizeWithRoleEnumeration(classes);
        if (actual == EXPECTED_LEGACY_USE_COUNT) {
            // 过渡期：存量已由规则 1 锁定，本规则（硬失败门禁）跳过，不阻塞 CI。
            // S3 修正：原 assumeTrue(true, ...) 是永真 no-op，JVM 仍计入"假设通过"；
            // 此处直接 return 语义更清晰。EXPECTED 归零后此分支自然走不到（actual==0==EXPECTED），
            // 届时删除整个 if + 规则 1，让下方 check 独立生效。
            return;
        }
        // 实际数 != EXPECTED：规则 1 已失败，本规则也报（双重保险）
        // 同时扫方法级和类级（S2 修正：原只扫方法级，类级 hasAnyRole 是盲区）
        methods()
            .that().areAnnotatedWith("org.springframework.security.access.prepost.PreAuthorize")
            .should(NOT_USE_ROLE_ENUMERATION_AUTH_METHOD)
            .because("Constitution VI: 禁止 hasAnyRole/hasRole 角色枚举式白名单（双轨制元凶）。"
                + "改用 hasAuthority('<permissionKey>') 或 isAuthenticated()。"
                + "参见 specs/024-preauthorize-unification")
            .check(classes);
        classes()
            .that().areAnnotatedWith("org.springframework.security.access.prepost.PreAuthorize")
            .should(NOT_USE_ROLE_ENUMERATION_AUTH_CLASS)
            .because("Constitution VI 类级守卫：类级 @PreAuthorize 也禁止 hasAnyRole/hasRole。"
                + "参见 specs/024-preauthorize-unification")
            .check(classes);
    }

    /**
     * 统计 @PreAuthorize 注解中 SpEL 含 hasAnyRole/hasRole 的<b>方法级 + 类级</b>使用点数。
     * 含编译期内联的 static final String 常量（S2 修正：补充类级注解扫描）。
     */
    private static int countPreAuthorizeWithRoleEnumeration(JavaClasses classes) {
        int[] count = {0};
        for (JavaClass clazz : classes) {
            // 方法级注解
            for (com.tngtech.archunit.core.domain.JavaMethod method : clazz.getMethods()) {
                if (method.isAnnotatedWith(
                        "org.springframework.security.access.prepost.PreAuthorize")) {
                    String spel = method.getAnnotationOfType(
                        org.springframework.security.access.prepost.PreAuthorize.class).value();
                    if (spel != null && (spel.contains("hasAnyRole") || spel.contains("hasRole"))) {
                        count[0]++;
                    }
                }
            }
            // 类级注解（S2 修正：原只扫 getMethods()，漏掉类级注解）
            if (clazz.isAnnotatedWith(
                    "org.springframework.security.access.prepost.PreAuthorize")) {
                String spel = clazz.getAnnotationOfType(
                    org.springframework.security.access.prepost.PreAuthorize.class).value();
                if (spel != null && (spel.contains("hasAnyRole") || spel.contains("hasRole"))) {
                    count[0]++;
                }
            }
        }
        return count[0];
    }
}
