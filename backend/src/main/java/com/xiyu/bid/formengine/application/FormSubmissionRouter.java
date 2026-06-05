// Input: scope / formData(Map) / operatorUsername
// Output: SubmitResult
// Pos: Application 层（编排层，调用 Service 执行创建）
// 维护声明: 编排逻辑，将动态表单 Map 数据映射到业务 DTO 并分发到对应服务.
//          数据映射委托给 FormSubmissionMappers（纯函数）。
package com.xiyu.bid.formengine.application;

import com.xiyu.bid.contractborrow.application.service.ContractBorrowCommandAppService;
import com.xiyu.bid.contractborrow.application.command.CreateContractBorrowCommand;
import com.xiyu.bid.formengine.domain.SubmitResult;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.service.ProjectService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.service.QualificationService;
import com.xiyu.bid.resources.dto.BarCertificateCreateRequest;
import com.xiyu.bid.resources.dto.ExpenseCreateRequest;
import com.xiyu.bid.resources.service.BarCertificateService;
import com.xiyu.bid.resources.service.ExpenseService;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.service.TenderCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.xiyu.bid.formengine.domain.SubmitResult.failure;
import static com.xiyu.bid.formengine.domain.SubmitResult.ok;

/**
 * 表单提交业务分发器。
 *
 * 根据 scope 将前端提交的动态表单数据（Map&lt;String, Object&gt;）映射为
 * 对应业务 DTO，并调用业务 Service 执行创建。
 * <p>
 * 此组件属于 Application 层（编排层），不含复杂业务规则，
 * 仅负责数据转换与路由分发。数据映射委托给 FormSubmissionMappers。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormSubmissionRouter {

    private final TenderCommandService tenderCommandService;
    private final ProjectService projectService;
    private final ExpenseService expenseService;
    private final BarCertificateService barCertificateService;
    private final QualificationService qualificationService;
    private final ContractBorrowCommandAppService contractBorrowCommandAppService;
    private final AuthService authService;

    /**
     * 分发表单提交到对应的业务处理器。
     *
     * @param scope             表单 scope（如 "tender.entry"、"project.basic"）
     * @param formData          前端提交的表单数据
     * @param operatorUsername  操作人用户名
     * @return 提交结果
     */
    public SubmitResult dispatch(String scope, Map<String, Object> formData, String operatorUsername) {
        log.debug("Dispatching form submission: scope={}, operator={}", scope, operatorUsername);
        return switch (scope) {
            case "tender.entry" -> handleTender(formData, operatorUsername);
            case "project.basic" -> handleProject(formData);
            case "resource.expense" -> handleExpense(formData, operatorUsername);
            case "resource.ca" -> handleCaCertificate(formData);
            case "resource.contract" -> handleContractBorrow(formData);
            case "knowledge.case" -> handleCase();
            case "knowledge.qual" -> handleQualification(formData);
            case "tender.evaluation", "project.evaluation" ->
                    failure("scope '" + scope + "' 的提交处理器尚在开发中");
            default -> failure("不支持的表单类型: " + scope);
        };
    }

    // ==================== tender.entry ====================

    private SubmitResult handleTender(Map<String, Object> formData, String operatorUsername) {
        try {
            TenderDTO dto = FormSubmissionMappers.toTenderDTO(formData);
            Long userId = authService.resolveUserIdByUsername(operatorUsername);
            if (userId == null) {
                log.warn("Could not resolve user '{}', tender created without creator", operatorUsername);
            }
            TenderDTO created = tenderCommandService.createTender(dto, userId);
            log.info("Tender created via form engine: id={}, title={}, userId={}", created.getId(), created.getTitle(), userId);
            return ok(Map.of(
                    "tenderId", created.getId() != null ? created.getId() : 0L,
                    "title", created.getTitle() != null ? created.getTitle() : ""
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Tender form validation failed: {}", e.getMessage());
            return failure("表单数据错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create tender via form engine: {}", e.getMessage(), e);
            return failure("创建标讯失败: " + e.getMessage());
        }
    }

    // ==================== project.basic ====================

    private SubmitResult handleProject(Map<String, Object> formData) {
        try {
            ProjectDTO dto = FormSubmissionMappers.toProjectDTO(formData);
            ProjectDTO created = projectService.createProject(dto);
            log.info("Project created via form engine: id={}, name={}", created.getId(), created.getName());
            return ok(Map.of(
                    "projectId", created.getId() != null ? created.getId() : 0L,
                    "name", created.getName() != null ? created.getName() : ""
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Project form validation failed: {}", e.getMessage());
            return failure("表单数据错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create project via form engine: {}", e.getMessage(), e);
            return failure("创建项目失败: " + e.getMessage());
        }
    }

    // ==================== resource.expense ====================

    private SubmitResult handleExpense(Map<String, Object> formData, String username) {
        try {
            ExpenseCreateRequest req = FormSubmissionMappers.toExpenseRequest(formData, username);
            var created = expenseService.createExpense(req);
            log.info("Expense created via form engine: id={}, amount={}", created.getId(), created.getAmount());
            return ok(Map.of("expenseId", created.getId() != null ? created.getId() : 0L));
        } catch (IllegalArgumentException e) {
            log.warn("Expense form validation failed: {}", e.getMessage());
            return failure("表单数据错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create expense via form engine: {}", e.getMessage(), e);
            return failure("创建费用申请失败: " + e.getMessage());
        }
    }

    // ==================== resource.ca ====================

    private SubmitResult handleCaCertificate(Map<String, Object> formData) {
        try {
            Long assetId = FormSubmissionMappers.toLong(formData.get("assetId"));
            if (assetId == null) return failure("assetId 为必填项");

            BarCertificateCreateRequest req = FormSubmissionMappers.toBarCertificateRequest(formData);
            var created = barCertificateService.createCertificate(assetId, req);
            log.info("CA certificate created via form engine: assetId={}, type={}", assetId, req.getType());
            return ok(Map.of("assetId", assetId, "type", req.getType() != null ? req.getType() : ""));
        } catch (IllegalArgumentException e) {
            return failure("表单数据错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create CA certificate via form engine: {}", e.getMessage(), e);
            return failure("创建CA证书失败: " + e.getMessage());
        }
    }

    // ==================== resource.contract ====================

    private SubmitResult handleContractBorrow(Map<String, Object> formData) {
        try {
            CreateContractBorrowCommand cmd = FormSubmissionMappers.toContractBorrowCommand(formData);
            var created = contractBorrowCommandAppService.create(cmd);
            log.info("Contract borrow created via form engine: id={}", created.id());
            return ok(Map.of("borrowId", created.id() != null ? created.id() : 0L));
        } catch (IllegalArgumentException e) {
            return failure("表单数据错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create contract borrow via form engine: {}", e.getMessage(), e);
            return failure("创建合同借阅失败: " + e.getMessage());
        }
    }

    // ==================== knowledge.case ====================

    private SubmitResult handleCase() {
        return failure("knowledge.case 提交处理器尚未实现，请联系管理员");
    }

    // ==================== knowledge.qual ====================

    private SubmitResult handleQualification(Map<String, Object> formData) {
        try {
            QualificationDTO dto = FormSubmissionMappers.toQualificationDTO(formData);
            var created = qualificationService.createQualification(dto);
            log.info("Qualification created via form engine: id={}", created.getId());
            return ok(Map.of("qualificationId", created.getId() != null ? created.getId() : 0L));
        } catch (IllegalArgumentException e) {
            return failure("表单数据错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create qualification via form engine: {}", e.getMessage(), e);
            return failure("创建资质失败: " + e.getMessage());
        }
    }
}
