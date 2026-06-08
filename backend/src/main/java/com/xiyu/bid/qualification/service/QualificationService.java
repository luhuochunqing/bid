// Input: compatibility DTOs, businessqualification application services, DTO mapper, and project access scope
// Output: legacy qualification API orchestration with project-linked borrow record access control
// Pos: Service/业务编排层
// 维护声明: 仅维护兼容入口编排；业务规则下沉到 businessqualification 子域。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.access.core.ProjectLinkedRecordVisibilityPolicy;
import com.xiyu.bid.alerts.service.QualificationExpiryNotificationService;
import com.xiyu.bid.businessqualification.application.service.AlertConfigAppService;
import com.xiyu.bid.businessqualification.application.service.BorrowQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.CreateQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.DeleteQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.GetQualificationBorrowRecordsAppService;
import com.xiyu.bid.businessqualification.application.service.ImportQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.ListQualificationsAppService;
import com.xiyu.bid.businessqualification.application.service.ReturnQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.UpdateQualificationAppService;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.dto.QualificationBorrowRecordDTO;
import com.xiyu.bid.qualification.dto.QualificationBorrowRequest;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.dto.QualificationOverviewDTO;
import com.xiyu.bid.qualification.dto.QualificationReturnRequest;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class QualificationService {

    private final CreateQualificationAppService createQualificationAppService;
    private final UpdateQualificationAppService updateQualificationAppService;
    private final BorrowQualificationAppService borrowQualificationAppService;
    private final ReturnQualificationAppService returnQualificationAppService;
    private final ListQualificationsAppService listQualificationsAppService;
    private final GetQualificationBorrowRecordsAppService getQualificationBorrowRecordsAppService;
    /** §4.1.3.8 资质到期通知编排（替代旧 ScanExpiringQualificationsAppService）。 */
    private final QualificationExpiryNotificationService qualificationExpiryNotificationService;
    private final AlertConfigAppService alertConfigAppService;
    private final DeleteQualificationAppService deleteQualificationAppService;
    private final ImportQualificationAppService importQualificationAppService;
    private final QualificationDtoMapper mapper;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final BusinessQualificationRepository businessQualificationRepository;
    private final QualificationExcelSupport qualificationExcelSupport;

    public QualificationDTO createQualification(QualificationDTO dto) {
        return mapper.toDto(createQualificationAppService.create(mapper.toUpsertCommand(dto)));
    }

    @Transactional(readOnly = true)
    public List<QualificationDTO> getAllQualifications(
            String subjectType,
            String subjectName,
            String category,
            List<String> status,
            String borrowStatus,
            Integer expiringWithinDays,
            LocalDate expiringFrom,
            LocalDate expiringTo,
            String issuer,
            String keyword
    ) {
        return listQualificationsAppService.list(
                        mapper.toCriteria(subjectType, subjectName, category, status, borrowStatus, expiringWithinDays, expiringFrom, expiringTo, issuer, keyword))
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public QualificationDTO getQualificationById(Long id) {
        return mapper.toDto(findQualification(id));
    }

    public QualificationDTO updateQualification(Long id, QualificationDTO dto) {
        return mapper.toDto(updateQualificationAppService.update(id, mapper.toUpsertCommand(dto)));
    }

    public void deleteQualification(Long id) {
        deleteQualificationAppService.delete(id);
    }

    @Transactional(readOnly = true)
    public List<QualificationDTO> getQualificationsByType(com.xiyu.bid.entity.Qualification.Type type) {
        return getAllQualifications(null, null, type == null ? null : mapper.toUpsertCommand(QualificationDTO.builder().type(type).build()).getCategory().name(), null, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<QualificationDTO> getValidQualifications() {
        return getAllQualifications(null, null, null, null, null, null, null, null, null, null).stream()
                .filter(item -> !"expired".equals(item.getStatus()))
                .toList();
    }

    public QualificationBorrowRecordDTO borrowQualification(Long id, QualificationBorrowRequest request) {
        Long projectId = parseBorrowProjectId(request == null ? null : request.getProjectId());
        assertCanAccessProject(projectId);
        if (request != null) {
            request.setProjectId(projectId == null ? null : projectId.toString());
        }
        QualificationLoan loan = borrowQualificationAppService.borrow(id, mapper.toBorrowCommand(request));
        return mapper.toBorrowRecordDto(loan, findQualification(id));
    }

    public QualificationBorrowRecordDTO returnQualification(Long id, QualificationReturnRequest request) {
        QualificationLoan activeLoan = getQualificationBorrowRecordsAppService.getBorrowRecords(id).stream()
                .filter(loan -> loan.getStatus() == LoanStatus.BORROWED)
                .findFirst()
                .orElse(null);
        if (activeLoan != null) {
            assertCanAccessProject(parseRecordProjectId(activeLoan.getProjectId()));
        }
        QualificationLoan loan = returnQualificationAppService.returnLoan(id, mapper.toReturnCommand(request));
        return mapper.toBorrowRecordDto(loan, findQualification(id));
    }

    public QualificationBorrowRecordDTO returnQualificationByRecordId(Long recordId, QualificationReturnRequest request) {
        QualificationLoan targetLoan = getQualificationBorrowRecordsAppService.getBorrowRecords().stream()
                .filter(loan -> recordId.equals(loan.getId()))
                .findFirst()
                .orElse(null);
        if (targetLoan != null) {
            assertCanAccessProject(parseRecordProjectId(targetLoan.getProjectId()));
        }
        QualificationLoan loan = returnQualificationAppService.returnLoanByRecordId(recordId, mapper.toReturnCommand(request));
        return mapper.toBorrowRecordDto(loan, findQualification(loan.getQualificationId()));
    }

    @Transactional(readOnly = true)
    public List<QualificationBorrowRecordDTO> getBorrowRecords(Long id) {
        if (id == null) {
            Map<Long, String> qualificationNameById = listQualificationsAppService.list(mapper.toCriteria(null, null, null, null, null, null, null, null, null, null))
                    .stream()
                    .collect(Collectors.toMap(BusinessQualification::id, BusinessQualification::name, (left, right) -> left));

            return filterVisibleLoans(getQualificationBorrowRecordsAppService.getBorrowRecords()).stream()
                    .map(loan -> mapper.toBorrowRecordDto(
                            loan,
                            qualificationNameById.getOrDefault(loan.getQualificationId(), "资质文件")
                    ))
                    .toList();
        }

        BusinessQualification qualification = findQualification(id);
        return filterVisibleLoans(getQualificationBorrowRecordsAppService.getBorrowRecords(id)).stream()
                .map(item -> mapper.toBorrowRecordDto(item, qualification))
                .toList();
    }

    @Transactional(readOnly = true)
    public QualificationOverviewDTO getOverview() {
        return mapper.toOverview(getAllQualifications(null, null, null, null, null, null, null, null, null, null));
    }

    /**
     * §4.1.3.8 蓝图：手动触发资质到期扫描。
     * <p>
     * 兼容原响应 shape：data 字段保持为整数（命中数）。
     * 通知/跳过明细仍可从服务端日志获取。
     *
     * @param thresholdDays 调用方传入的阈值；<=0 时使用 AlertConfig.alertDays
     * @return 扫描命中数（已下发通知 + 被跳过的证书总数）
     */
    @Transactional
    public int scanExpiringQualifications(int thresholdDays) {
        int effective = thresholdDays > 0
                ? thresholdDays
                : alertConfigAppService.getConfig().alertDays();
        QualificationExpiryNotificationService.ScanOutcome outcome =
                qualificationExpiryNotificationService.runScan(effective, null);
        return outcome.scanned();
    }

    private BusinessQualification findQualification(Long id) {
        return listQualificationsAppService.get(id);
    }

    private List<QualificationLoan> filterVisibleLoans(List<QualificationLoan> loans) {
        boolean admin = projectAccessScopeService.currentUserHasAdminAccess();
        List<Long> allowedProjectIds = admin ? List.of() : projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        return loans.stream()
                .filter(loan -> ProjectLinkedRecordVisibilityPolicy.visible(admin, allowedProjectIds, parseRecordProjectId(loan.getProjectId())))
                .toList();
    }

    private Long parseBorrowProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        String normalized = projectId.trim();
        if (!normalized.matches("\\d+")) {
            throw new InvalidArgumentException("项目 ID 必须为数字");
        }
        return Long.valueOf(normalized);
    }

    private Long parseRecordProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        String normalized = projectId.trim();
        return normalized.matches("\\d+") ? Long.valueOf(normalized) : -1L;
    }

    private void assertCanAccessProject(Long projectId) {
        if (projectId != null) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        }
    }

    // TODO(leaf #4): async export with message notification for >500 records per blueprint
    public void exportExcel(String keyword, String status, java.io.OutputStream out) throws java.io.IOException {
        var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var sh = wb.createSheet("资质证书台账");
        var hr = sh.createRow(0);
        String[] cols = {"证书名称","等级","认证机构","证书编号","发证日期","有效期","代理机构","代理联系方式","认证范围","状态"};
        for (int i = 0; i < cols.length; i++) hr.createCell(i).setCellValue(cols[i]);
        int r = 1;
        for (var q : getAllQualifications(null, null, null, status == null ? null : List.of(status), null, null, null, null, null, keyword)) {
            var row = sh.createRow(r++);
            row.createCell(0).setCellValue(nullToEmpty(q.getName()));
            row.createCell(1).setCellValue(q.getLevel() != null ? q.getLevel().name() : "");
            row.createCell(2).setCellValue(nullToEmpty(q.getIssuer()));
            row.createCell(3).setCellValue(nullToEmpty(q.getCertificateNo()));
            row.createCell(4).setCellValue(q.getIssueDate() != null ? q.getIssueDate().toString() : "");
            row.createCell(5).setCellValue(q.getExpiryDate() != null ? q.getExpiryDate().toString() : "");
            row.createCell(6).setCellValue(nullToEmpty(q.getAgency()));
            row.createCell(7).setCellValue(nullToEmpty(q.getAgencyContact()));
            row.createCell(8).setCellValue(nullToEmpty(q.getCertScope()));
            row.createCell(9).setCellValue(statusLabel(q.getStatus()));
        }
        wb.write(out); wb.close();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String statusLabel(String status) {
        if (status == null) return "";
        return switch (status.toLowerCase()) {
            case "valid", "in_stock" -> "在库";
            case "expiring" -> "即将到期";
            case "expired" -> "已过期";
            case "retired" -> "已下架";
            default -> status;
        };
    }

    public void generateTemplate(java.io.OutputStream out) throws java.io.IOException {
        var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var sh = wb.createSheet("资质证书");
        var hr = sh.createRow(0);
        String[] cols = {"证书名称","等级","认证机构","证书编号","发证日期","证书有效期","代理机构","代理联系方式","认证范围","证书审核提醒","附件文件名"};
        for (int i = 0; i < cols.length; i++) hr.createCell(i).setCellValue(cols[i]);
        wb.write(out); wb.close();
    }

    @Transactional
    public QualificationDTO retireQualification(Long id, String reason) {
        var dto = getQualificationById(id);
        dto.setStatus("RETIRED");
        dto.setRetireReason(reason);
        updateQualificationAppService.update(id, mapper.toUpsertCommand(dto));
        return getQualificationById(id);
    }

    @Transactional
    public QualificationDTO restoreQualification(Long id) {
        var dto = getQualificationById(id);
        var policy = new com.xiyu.bid.businessqualification.domain.service.QualificationExpiryPolicy();
        var period = new com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod(
                dto.getIssueDate(), dto.getExpiryDate());
        dto.setStatus(policy.evaluate(period, java.time.LocalDate.now()).name());
        dto.setRetireReason("");
        updateQualificationAppService.update(id, mapper.toUpsertCommand(dto));
        return getQualificationById(id);
    }

    @Transactional(readOnly = true)
    public List<String> getAllLevels() {
        return businessQualificationRepository.findAllLevels();
    }

    @Transactional(readOnly = true)
    public byte[] batchExportExcel(List<Long> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidArgumentException("导出 ID 列表不能为空");
        }
        List<QualificationDTO> items = getAllQualifications(null, null, null, null, null, null, null, null, null, null)
                .stream()
                .filter(q -> ids.contains(q.getId()))
                .toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        qualificationExcelSupport.writeLedger(items, null, out);
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] batchExportZip(List<Long> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidArgumentException("下载 ID 列表不能为空");
        }
        List<QualificationDTO> items = getAllQualifications(null, null, null, null, null, null, null, null, null, null)
                .stream()
                .filter(q -> ids.contains(q.getId()))
                .toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out)) {
            for (QualificationDTO item : items) {
                if (item.getAttachments() != null) {
                    for (var att : item.getAttachments()) {
                        if (att.getFileUrl() == null || att.getFileUrl().isBlank()) continue;
                        String entryName = (item.getName() == null ? "资质" : item.getName()) + "_" + (att.getFileName() == null ? att.getFileUrl() : att.getFileName());
                        zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                        try (java.io.InputStream in = new java.net.URL(att.getFileUrl()).openStream()) {
                            in.transferTo(zos);
                        } catch (java.net.MalformedURLException e) {
                            zos.write(("无法下载: " + att.getFileUrl()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                        zos.closeEntry();
                    }
                }
                if (item.getFileUrl() != null && !item.getFileUrl().isBlank()) {
                    String entryName = (item.getName() == null ? "资质" : item.getName()) + "_" + item.getFileUrl();
                    zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                    try (java.io.InputStream in = new java.net.URL(item.getFileUrl()).openStream()) {
                        in.transferTo(zos);
                    } catch (java.net.MalformedURLException e) {
                        zos.write(("无法下载: " + item.getFileUrl()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    zos.closeEntry();
                }
            }
        }
        return out.toByteArray();
    }

    @Transactional
    public ImportQualificationAppService.ImportSummary importFromExcel(MultipartFile file, String operatorName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }
        return importQualificationAppService.importFromExcel(file, operatorName);
    }

    @Transactional
    public QualificationDTO uploadAttachment(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }
        QualificationDTO dto = getQualificationById(id);
        dto.setFileUrl(file.getOriginalFilename());
        return updateQualification(id, dto);
    }
}
