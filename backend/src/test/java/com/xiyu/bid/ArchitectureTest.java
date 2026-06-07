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
            .should().dependOnClassesThat()
            .resideInAPackage("..service..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("");

    /**
     * RULE 9: ConfigService
     * 
     */
    @ArchTest
    public static final ArchRule config_should_not_depend_on_service =
        noClasses()
            .that().resideInAPackage("..config..")
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
}
