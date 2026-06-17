package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.domain.importvalidation.ParsedCertificateRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedEducationRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedPersonnelRow;
import com.xiyu.bid.personnel.domain.model.importtask.ImportErrorDetail;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.CertificateType;
import com.xiyu.bid.personnel.domain.valueobject.Education;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelExcelImporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
class PersonnelImportExecutor {

    private final PersonnelRepository personnelRepository;

    @Transactional
    public ImportResult executeImport(
            PersonnelExcelImporter.ImportResult result,
            ImportProgressCallback callback
    ) {
        List<ImportErrorDetail> errorDetails = new ArrayList<>();

        callback.onProgress("正在导入人员数据...", 40);
        Map<String, Long> empNoToId = importPersonnelRows(result.personnelRows(), errorDetails);
        callback.onProgress("基础信息导入完成", 60);

        callback.onProgress("正在导入教育经历...", 70);
        importEducationRows(result.educationRows(), empNoToId, errorDetails);
        callback.onProgress("教育经历导入完成", 80);

        callback.onProgress("正在导入证书信息...", 90);
        importCertificateRows(result.certificateRows(), empNoToId, errorDetails);
        callback.onProgress("证书信息导入完成", 95);

        int successCount = result.totalRows() - errorDetails.size();
        int failureCount = errorDetails.size();
        int warningCount = result.validationResult().warnings().size();

        return new ImportResult(result.totalRows(), successCount, failureCount, warningCount, errorDetails);
    }

    private Map<String, Long> importPersonnelRows(List<ParsedPersonnelRow> rows,
                                                  List<ImportErrorDetail> errorDetails) {
        Map<String, Long> empNoToId = new ConcurrentHashMap<>();

        for (ParsedPersonnelRow row : rows) {
            try {
                Long personnelId = createOrUpdatePersonnel(row);
                empNoToId.put(row.employeeNumber(), personnelId);
            } catch (RuntimeException e) {
                log.error("导入人员失败: {}", row.employeeNumber(), e);
                errorDetails.add(new ImportErrorDetail(
                        "基础信息", row.excelRow(), row.employeeNumber(), row.name(),
                        "创建人员失败: " + e.getMessage()
                ));
            }
        }

        return empNoToId;
    }

    private Long createOrUpdatePersonnel(ParsedPersonnelRow row) {
        List<com.xiyu.bid.personnel.domain.model.Personnel> existing =
                personnelRepository.findByEmployeeNumber(row.employeeNumber());

        com.xiyu.bid.personnel.domain.model.Personnel personnel;
        if (!existing.isEmpty()) {
            com.xiyu.bid.personnel.domain.model.Personnel p = existing.get(0);
            List<Certificate> certs = p.certificates();
            List<Education> edus = p.educations();
            personnel = new com.xiyu.bid.personnel.domain.model.Personnel(
                    p.id(), row.name(), row.employeeNumber(),
                    p.departmentCode(), row.departmentName() != null ? row.departmentName() : p.departmentName(),
                    row.gender(), row.entryDate(), row.birthDate(),
                    row.phone(), row.education(), row.technicalTitle(),
                    p.status(), p.attachmentUrl(), row.remark() != null ? row.remark() : p.remark(),
                    certs, edus,
                    p.createdAt(), LocalDateTime.now()
            );
        } else {
            personnel = com.xiyu.bid.personnel.domain.model.Personnel.create(
                    null,
                    row.name(),
                    row.employeeNumber(),
                    null,
                    row.departmentName(),
                    row.gender(),
                    row.entryDate(),
                    row.birthDate(),
                    row.phone(),
                    row.education(),
                    row.technicalTitle(),
                    PersonnelStatus.ACTIVE,
                    null,
                    row.remark(),
                    List.of(),
                    List.of()
            );
        }

        return personnelRepository.save(personnel).id();
    }

    private void importEducationRows(List<ParsedEducationRow> rows,
                                    Map<String, Long> empNoToPersonnelId,
                                    List<ImportErrorDetail> errorDetails) {
        for (ParsedEducationRow row : rows) {
            Long personnelId = empNoToPersonnelId.get(row.employeeNumber());
            if (personnelId == null) {
                errorDetails.add(new ImportErrorDetail(
                        "教育经历", row.excelRow(), row.employeeNumber(), row.name(),
                        "关联的人员不存在"
                ));
                continue;
            }

            try {
                Education education = new Education(
                        row.schoolName(),
                        row.startDate(),
                        row.endDate(),
                        row.highestEducation(),
                        row.studyForm(),
                        row.major(),
                        Boolean.TRUE.equals(row.isHighestEducationSchool())
                );
                personnelRepository.addEducation(personnelId, education);
            } catch (RuntimeException e) {
                errorDetails.add(new ImportErrorDetail(
                        "教育经历", row.excelRow(), row.employeeNumber(), row.name(),
                        "添加教育经历失败: " + e.getMessage()
                ));
            }
        }
    }

    private void importCertificateRows(List<ParsedCertificateRow> rows,
                                      Map<String, Long> empNoToPersonnelId,
                                      List<ImportErrorDetail> errorDetails) {
        for (ParsedCertificateRow row : rows) {
            Long personnelId = empNoToPersonnelId.get(row.employeeNumber());
            if (personnelId == null) {
                errorDetails.add(new ImportErrorDetail(
                        "证书与职称", row.excelRow(), row.employeeNumber(), row.name(),
                        "关联的人员不存在"
                ));
                continue;
            }

            try {
                CertificateType certType = parseCertificateType(row.type());
                Certificate certificate = new Certificate(
                        null,
                        row.certificateName(),
                        row.certificateNumber(),
                        certType,
                        row.issueDate(),
                        row.expiryDate(),
                        null,
                        row.title(),
                        Boolean.TRUE.equals(row.isPermanent()),
                        row.remark()
                );
                personnelRepository.addCertificate(personnelId, certificate);
            } catch (RuntimeException e) {
                errorDetails.add(new ImportErrorDetail(
                        "证书与职称", row.excelRow(), row.employeeNumber(), row.name(),
                        "添加证书失败: " + e.getMessage()
                ));
            }
        }
    }

    private CertificateType parseCertificateType(String type) {
        if (type == null || type.isBlank()) {
            return CertificateType.OTHER;
        }
        try {
            return CertificateType.valueOf(type.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return CertificateType.OTHER;
        }
    }

    public record ImportResult(
            int totalCount,
            int successCount,
            int failureCount,
            int warningCount,
            List<ImportErrorDetail> errorDetails
    ) {}

    public interface ImportProgressCallback {
        void onProgress(String message, int percent);
    }
}
