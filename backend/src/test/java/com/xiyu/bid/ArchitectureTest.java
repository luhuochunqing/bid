// Input: ArchUnit framework
// Output: Architecture validation rules
// Pos: Test/
//  md

package com.xiyu.bid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.Slice;
import com.xiyu.bid.architecture.fixtures.TomapFixture2Arg;
import com.xiyu.bid.architecture.fixtures.TomapFixture3Arg;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @ArchTest
    public static final ArchRule excel_date_cell_must_use_excel_cell_formatter =
        noClasses()
            .that().resideOutsideOfPackage("com.xiyu.bid.infrastructure.excel..")
            .should().callMethod(org.apache.poi.ss.usermodel.DataFormatter.class, "formatCellValue",
                org.apache.poi.ss.usermodel.Cell.class)
            .andShould().callMethod(org.apache.poi.ss.usermodel.DataFormatter.class, "formatCellValue",
                org.apache.poi.ss.usermodel.Cell.class, org.apache.poi.ss.usermodel.FormulaEvaluator.class)
            .because("CO-505: 直接调用 DataFormatter.formatCellValue() 会导致 Excel 日期格式单元格输出不稳定 "
                + "(取决于单元格 number format)，下游 CommonDateParser 可能无法解析。"
                + "必须通过 ExcelCellFormatter.formatCell() 统一处理，日期单元格统一输出 yyyy-MM-dd ISO 格式。");

    // Constitution VI hasAnyRole/hasRole 总数守卫已于 2026-07-03 移除（PR 本 commit）。
    // 原因：P3 机械迁移方向已废弃（转向功能级权限审计），守卫只锁语法不锁业务语义，
    // 且阻塞所有改 @PreAuthorize 的 backend PR。真实权限语义已由 50 个功能级契约测试锁定
    // （见 docs/audit/tender-permission-audit-2026-07-03.md + project-permission-audit-2026-07-03.md）。
    // 防漂移靠契约测试（锁定角色/数据范围/状态收口），而非语法层面的 hasAnyRole 计数。

    // ========== T011/T014: Constitution v2.0.0 Principle VII — Collectors.toMap merge function 守卫 ==========

    /** 豁免清单：US2 Phase 1-3 遗留 2-arg toMap 调用，从 scripts/tomap-exemptions.json 加载。 */
    private static final Set<String> TOMAP_EXEMPTIONS = loadTomapExemptions();

    /**
     * 从 scripts/tomap-exemptions.json 加载豁免清单。
     * 返回 Set&lt;"file:line"&gt;，file 为相对路径如 com/xiyu/bid/.../XxxService.java。
     * 文件不存在或解析失败时返回空集（strict 模式：所有 2-arg 调用均被标记）。
     */
    private static Set<String> loadTomapExemptions() {
        Set<String> exemptions = new HashSet<>();
        String[] candidates = {
            "scripts/tomap-exemptions.json",
            "../scripts/tomap-exemptions.json"
        };
        File file = null;
        for (String p : candidates) {
            File f = new File(p);
            if (f.exists()) { file = f; break; }
        }
        if (file == null) return exemptions;
        try {
            JsonNode root = new ObjectMapper().readTree(file);
            JsonNode list = root.get("exemptions");
            if (list != null) {
                for (JsonNode e : list) {
                    exemptions.add(e.get("file").asText() + ":" + e.get("line").asInt());
                }
            }
        } catch (Exception ex) {
            // 解析失败 → 空集（strict 模式）
        }
        return exemptions;
    }

    /**
     * ArchCondition: 禁止 Collectors.toMap 2-arg 调用（无 merge function）。
     * 2-arg toMap(k, v) 在遇到重复 key 时抛 IllegalStateException，
     * 必须使用 3-arg toMap(k, v, (a, b) -> a) 优雅降级。
     */
    private static final ArchCondition<JavaClass> NO_TOMAP_WITHOUT_MERGE_FUNCTION =
        new ArchCondition<JavaClass>(
            "not call Collectors.toMap without merge function (Constitution v2.0.0 Principle VII)"
        ) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    // Use var: call.getTarget() returns AccessTarget.MethodCallTarget
                    // which may not be directly importable in all ArchUnit versions.
                    // Use getRawParameterTypes() to inspect argument count without resolving overload.
                    var target = call.getTarget();
                    if (target == null) continue;
                    if (!"java.util.stream.Collectors".equals(target.getOwner().getName())) continue;
                    if (!"toMap".equals(target.getName())) continue;
                    // 2-arg toMap has exactly 2 raw parameter types (Function, Function).
                    // 3-arg has 3 (Function, Function, BinaryOperator); 4-arg has 4 (+ Supplier).
                    if (target.getRawParameterTypes().size() != 2) continue;
                    String className = item.getName();
                    int dollarIdx = className.indexOf('$');
                    if (dollarIdx > 0) className = className.substring(0, dollarIdx);
                    String filePath = className.replace('.', '/') + ".java";
                    int lineNum = call.getLineNumber();
                    String key = filePath + ":" + lineNum;
                    if (TOMAP_EXEMPTIONS.contains(key)) continue;
                    events.add(SimpleConditionEvent.violated(call,
                        "Collectors.toMap without merge function at " + item.getSimpleName()
                        + ".java:" + lineNum
                        + " — throws IllegalStateException on duplicate keys. "
                        + "Add (a, b) -> a as third argument. "
                        + "See Constitution v2.0.0 Principle VII."));
                }
            }
        };

    /**
     * RULE 18: Collectors.toMap 必须带 merge function（Constitution v2.0.0 Principle VII）
     *
     * 2 参数版本 toMap(k, v) 在遇到重复 key 时抛 IllegalStateException。
     * 必须使用 3 参数版本 toMap(k, v, (a, b) -> a) 优雅降级。
     *
     * 豁免清单说明：scripts/tomap-exemptions.json 当前为空（35 处已全部修复）。
     * 本规则在编译期基于字节码 JavaMethodCall API 扫描，覆盖所有调用形式（含
     * 静态导入 `import static ... toMap`）。pre-push gate 脚本
     * check-tomap-no-merge-function.mjs 是文本扫描兜底，与本规则形成双重守卫。
     */
    @ArchTest
    public static final ArchRule toMapMustHaveMergeFunction =
        classes()
            .should(NO_TOMAP_WITHOUT_MERGE_FUNCTION)
            .because("Collectors.toMap without merge function throws IllegalStateException "
                + "on duplicate keys. Add (a, b) -> a as third argument. "
                + "See Constitution v2.0.0 Principle VII.");

    /**
     * T014: 验证 toMapMustHaveMergeFunction 规则对 fixture 类的行为。
     * - 2-arg toMap → 应被标记（AssertionError）
     * - 3-arg toMap → 应通过
     */
    @Test
    public void tomapRule_shouldFlag2ArgAndPass3Arg() {
        JavaClasses violating = new ClassFileImporter()
            .importClasses(TomapFixture2Arg.class);
        assertThrows(AssertionError.class,
            () -> toMapMustHaveMergeFunction.check(violating),
            "2-arg Collectors.toMap should be flagged by toMapMustHaveMergeFunction");

        JavaClasses ok = new ClassFileImporter()
            .importClasses(TomapFixture3Arg.class);
        toMapMustHaveMergeFunction.check(ok);
    }

    /**
     * RULE 19: @Async 方法禁止同类内自调用（CO-560 防复发守卫）
     *
     * <p>根因：Spring AOP 代理无法拦截同类内 {@code this.} 调用，{@code @Async} 注解失效，
     * 异步方法同步执行。CO-560 中 PlatformAccountImportAppService.executeImportAsync 自调用
     * 导致异步失效 + 事务共享，单行 DB 异常触发 Hibernate Session 中毒 + UnexpectedRollbackException。
     *
     * <p>正确做法：将 @Async 方法提取到独立 Bean，跨类调用让代理生效（参见 §31 spec 031 R-002 决策）。
     *
     * <p>豁免清单：以下历史违规类暂缓治理（已记录技术债，后续逐步迁移）。
     */
    private static final Set<String> ASYNC_SELF_INVOCATION_EXEMPTIONS = Set.of(
        "CaCertificateImportAppService",
        "TenderImportAppService",
        "WarehouseExportAppService",
        "WarehouseImportAppService",
        "WarehouseLedgerExportAppService"
    );

    @ArchTest
    public static final ArchRule async_methods_should_not_be_self_invoked =
        noClasses()
            .should(new ArchCondition<JavaClass>("not self-invoke @Async methods within the same class") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    // 豁免历史违规类（后续逐步治理）
                    if (ASYNC_SELF_INVOCATION_EXEMPTIONS.contains(javaClass.getSimpleName())) {
                        return;
                    }
                    for (JavaMethod method : javaClass.getMethods()) {
                        for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                            // 只检查同类内的调用
                            if (!call.getTarget().getOwner().equals(javaClass)) continue;
                            String targetName = call.getTarget().getName();
                            // 在同类中查找目标方法，检查是否有 @Async 注解
                            for (JavaMethod targetMethod : javaClass.getMethods()) {
                                if (targetMethod.getName().equals(targetName) &&
                                    targetMethod.isAnnotatedWith(
                                        org.springframework.scheduling.annotation.Async.class)) {
                                    events.add(SimpleConditionEvent.violated(call,
                                        javaClass.getSimpleName() + "." + method.getName() +
                                        " self-invokes @Async method " + targetName +
                                        " — Spring AOP proxy cannot intercept this. " +
                                        "Extract to a separate bean (see spec 031 R-002)."));
                                    break;
                                }
                            }
                        }
                    }
                }
            })
            .because("@Async self-invocation bypasses Spring AOP proxy — the annotation has no effect. "
                + "Extract the @Async method to a separate bean so the proxy can intercept the cross-class call. "
                + "See CO-560 and spec 031 R-002.");
}
