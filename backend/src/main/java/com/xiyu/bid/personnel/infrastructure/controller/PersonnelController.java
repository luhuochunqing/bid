package com.xiyu.bid.personnel.infrastructure.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.personnel.application.command.PersonnelUpsertCommand;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.response.PersonnelEditResponse;
import com.xiyu.bid.personnel.application.result.PersonnelUpdateResult;
import com.xiyu.bid.personnel.application.dto.PersonnelOperationLogDTO;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.application.service.CreatePersonnelAppService;
import com.xiyu.bid.personnel.application.service.DeletePersonnelAppService;
import com.xiyu.bid.personnel.application.service.ListPersonnelAppService;
import com.xiyu.bid.personnel.application.service.PersonnelOperationLogService;
import com.xiyu.bid.personnel.application.service.RestorePersonnelAppService;
import com.xiyu.bid.personnel.application.service.UpdatePersonnelAppService;
import com.xiyu.bid.personnel.domain.port.PersonnelFileStorage;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelCertificateJpaRepository;
import com.xiyu.bid.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge/personnel")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class PersonnelController {

    private final CreatePersonnelAppService createService;
    private final UpdatePersonnelAppService updateService;
    private final DeletePersonnelAppService deleteService;
    private final RestorePersonnelAppService restoreService;
    private final ListPersonnelAppService listService;
    private final PersonnelOperationLogService operationLogService;

    private final PersonnelFileStorage fileStorage;
    private final PersonnelCertificateJpaRepository certJpaRepository;
    private final UserRepository userRepository;

    @PostMapping
    // 严格按照蓝图 4.3「人员证书」权限矩阵：仅投标部门三个角色可新增
    // 其他角色（项目负责人、行政人员、跨部门协同人员等）均无权限
    @PreAuthorize("hasAnyAuthority('/bidAdmin', 'bid-TeamLeader', 'bid-Team')")
    @Auditable(action = "CREATE", entityType = "Personnel", description = "创建人员")
    public ResponseEntity<ApiResponse<PersonnelDTO>> create(@Valid @RequestBody PersonnelUpsertCommand command,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long currentUserId = extractUserId(userDetails);
        String operatorName = resolveOperatorName(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("人员创建成功", createService.create(command, currentUserId, operatorName)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "READ", entityType = "Personnel", description = "获取人员列表")
    public ResponseEntity<ApiResponse<List<PersonnelDTO>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String certificateType,

            // === 新增筛选参数（筛选与搜索 h5） ===
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) List<String> highestEducations,
            @RequestParam(required = false) List<String> studyForms,
            @RequestParam(required = false) String majorKeyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDateTo,
            @RequestParam(required = false) String certificateKeyword,
            @RequestParam(required = false) List<String> certificateStatuses
    ) {
        var criteria = com.xiyu.bid.personnel.application.command.PersonnelListCriteria.ofFull(
                keyword, departmentCode,
                status != null ? PersonnelStatus.valueOf(status.toUpperCase()) : null,
                certificateType,
                gender, highestEducations, studyForms, majorKeyword,
                entryDateFrom, entryDateTo, certificateKeyword, certificateStatuses, null
        );
        return ResponseEntity.ok(ApiResponse.success("人员列表获取成功", listService.list(criteria)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "READ", entityType = "Personnel", description = "获取人员详情")
    public ResponseEntity<ApiResponse<PersonnelDTO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("人员详情获取成功", listService.get(id)));
    }

    @PutMapping("/{id}")
    // 按蓝图 4.3「编辑证书」要求：仅 本人(bid-Team) + 投标组长(bid-TeamLeader) + 投标管理员(bidAdmin) 可编辑
    @PreAuthorize("""
        hasAnyAuthority('/bidAdmin', 'bid-TeamLeader') or
        (hasAuthority('bid-Team') and #id == authentication.principal.id)
        """)
    @Auditable(action = "UPDATE", entityType = "Personnel", description = "更新人员")
    public ResponseEntity<ApiResponse<PersonnelEditResponse>> update(@PathVariable Long id,
            @Valid @RequestBody PersonnelUpsertCommand command,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long currentUserId = extractUserId(userDetails);
        String operatorName = resolveOperatorName(userDetails);
        PersonnelUpdateResult internalResult = updateService.update(id, command, currentUserId, operatorName);

        PersonnelEditResponse response = new PersonnelEditResponse(
                internalResult.personnel(),
                internalResult.warnings()
        );

        String message = response.hasWarnings()
                ? "人员更新成功（包含工号变更等警示）"
                : "人员更新成功";

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('/bidAdmin', 'bid-TeamLeader')")
    @Auditable(action = "DELETE", entityType = "Personnel", description = "删除人员")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestBody(required = false) DeletePersonnelRequest request,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "管理员执行删除操作";
        Long currentUserId = extractUserId(userDetails);
        String operatorName = userDetails != null ? userDetails.getUsername() : "system";
        deleteService.delete(id, reason, currentUserId, operatorName);
        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(UserDetails userDetails) {
        return 0L;
    }

    private String resolveOperatorName(UserDetails userDetails) {
        if (userDetails == null) return "system";
        String username = userDetails.getUsername();
        return userRepository.findByUsername(username)
                .map(user -> {
                    String fullName = user.getFullName();
                    return (fullName != null && !fullName.isBlank()) ? fullName + "（" + username + "）" : username;
                })
                .orElse(username);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyAuthority('/bidAdmin', 'bid-TeamLeader')")
    @Auditable(action = "RESTORE", entityType = "Personnel", description = "恢复已停用人员")
    public ResponseEntity<Void> restore(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        Long currentUserId = extractUserId(userDetails);
        String operatorName = userDetails != null ? userDetails.getUsername() : "system";
        restoreService.restore(id, currentUserId, operatorName);
        return ResponseEntity.ok().build();
    }

    /**
     * 上传/替换人员证书附件。
     * 用于 4.3 "新增证书" / "编辑证书" h5 Tab3 "证书附件" 字段（必填，PDF/JPG/PNG ≤10MB）。
     * 保存后返回可下载的 attachmentUrl，详情页 "下载" 链接可直接使用。
     */
    @PostMapping("/{personnelId}/certificates/{certId}/attachment")
    @PreAuthorize("hasAnyAuthority('/bidAdmin', 'bid-TeamLeader', 'bid-Team')")
    @Auditable(action = "UPDATE", entityType = "PersonnelCertificate", description = "上传/替换证书附件")
    public ResponseEntity<ApiResponse<String>> uploadCertAttachment(
            @PathVariable Long personnelId,
            @PathVariable Long certId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("附件不能超过10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null ||
            !(contentType.equals("application/pdf") || contentType.equals("image/jpeg") || contentType.equals("image/png"))) {
            throw new IllegalArgumentException("仅支持 PDF/JPG/PNG");
        }

        var certOpt = certJpaRepository.findById(certId);
        if (certOpt.isEmpty() || !personnelId.equals(certOpt.get().getPersonnelId())) {
            throw new IllegalArgumentException("证书不存在或不属于该人员");
        }

        String oldFileName = extractFileNameFromUrl(certOpt.get().getAttachmentUrl());
        String newFileName = file.getOriginalFilename();
        String operatorName = userDetails != null ? userDetails.getUsername() : "system";
        Long currentUserId = extractUserId(userDetails);

        String url;
        try {
            byte[] bytes = file.getBytes();
            url = fileStorage.storeCertAttachment(personnelId, certId, bytes, file.getOriginalFilename(), file.getContentType());
        } catch (java.io.IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }

        var cert = certOpt.get();
        cert.setAttachmentUrl(url);
        certJpaRepository.save(cert);

        // 记录附件替换操作日志（PRD 4.3.1.8: 替换证书附件）
        if (oldFileName != null) {
            operationLogService.save(PersonnelOperationLog.create(
                    personnelId,
                    currentUserId,
                    operatorName,
                    PersonnelOperationLog.OperationType.ATTACHMENT_REPLACE,
                    java.util.List.of(
                            new PersonnelOperationLog.ChangeDetail("attachment", oldFileName, newFileName)
                    )
            ));
        }

        return ResponseEntity.ok(ApiResponse.success("证书附件上传成功", url));
    }

    private String extractFileNameFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return url;
    }

    /**
     * 查询人员操作日志（4.3.1.3 详情抽屉 Tab 4）。
     */
    @GetMapping("/{id}/operation-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "READ", entityType = "PersonnelOperationLog", description = "查询人员操作日志")
    public ResponseEntity<ApiResponse<List<PersonnelOperationLogDTO>>> getOperationLogs(@PathVariable Long id) {
        var logs = operationLogService.findByPersonnelId(id);
        var dtos = logs.stream()
                .map(PersonnelOperationLogDTO::fromDomain)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("操作日志获取成功", dtos));
    }

    /**
     * 提供证书附件下载，使详情页 "下载" 链接可用。
     */
    @GetMapping("/attachments/{personnelId}/{filename:.+}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Resource> downloadCertAttachment(
            @PathVariable Long personnelId,
            @PathVariable String filename) {
        try {
            Path path = Paths.get("data/personnel-attachments", personnelId.toString(), filename);
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(path);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (RuntimeException e) {
            log.error("下载人员证书附件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
