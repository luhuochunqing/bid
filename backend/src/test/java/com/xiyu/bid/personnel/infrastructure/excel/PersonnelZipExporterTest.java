package com.xiyu.bid.personnel.infrastructure.excel;

import com.xiyu.bid.personnel.application.dto.CertificateDTO;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PersonnelZipExporter 单元测试
 *
 * CO-469 第三轮：防复发测试
 * 验证 employeeNumber=null 时不会抛 NPE（原 bug：Collectors.toMap null key → NPE → 进度卡 70%）
 */
class PersonnelZipExporterTest {

    private PersonnelExcelExporter excelExporter;
    private PersonnelZipExporter zipExporter;

    @BeforeEach
    void setUp() throws Exception {
        excelExporter = mock(PersonnelExcelExporter.class);
        when(excelExporter.export(anyList())).thenReturn(new byte[]{0x50, 0x4B}); // PK 头
        zipExporter = new PersonnelZipExporter(excelExporter);
    }

    /**
     * 构造测试用 CertificateDTO（13 个字段）
     */
    private CertificateDTO buildCert(Long id, String name, String certNo, String attachmentUrl) {
        return new CertificateDTO(
                id, name, certNo, null, null, null,
                attachmentUrl, name, false, null,
                false, 0L, "VALID"
        );
    }

    /**
     * 构造测试用 PersonnelDTO（21 个字段）
     */
    private PersonnelDTO buildPersonnel(Long id, String name, String empNo, List<CertificateDTO> certs) {
        return new PersonnelDTO(
                id, name, empNo, "DEPT01", "技术部", "男",
                null, null, "13800000000", "本科", "工程师",
                PersonnelStatus.ACTIVE, null, null,
                certs, List.of(), 0, null, "本科",
                certs.size(), 0, null, null
        );
    }

    @Test
    void exportZip_当员工工号为null_应跳过该员工不抛NPE() {
        // CO-469 第三轮：employeeNumber=null 是合法数据（OSS 同步层未填工号）
        // 原 bug：Collectors.toMap(PersonnelDTO::employeeNumber, ...) 在 key=null 时抛 NPE
        // 修复：filter(p -> p.employeeNumber() != null) 跳过
        PersonnelDTO personWithNullEmpNo = buildPersonnel(
                1L, "张三", null,
                List.of(buildCert(1L, "一级建造师", "CERT001", null))
        );

        assertThatCode(() -> zipExporter.exportZip(List.of(personWithNullEmpNo)))
                .doesNotThrowAnyException();
    }

    @Test
    void exportZip_混合工号null和有值_应正常导出不抛NPE() {
        PersonnelDTO personWithNullEmpNo = buildPersonnel(
                1L, "张三", null,
                List.of(buildCert(1L, "一级建造师", "CERT001", null))
        );
        PersonnelDTO personWithEmpNo = buildPersonnel(
                2L, "李四", "EMP002",
                List.of(buildCert(2L, "安全员B证", "CERT002", null))
        );

        assertThatCode(() -> zipExporter.exportZip(List.of(personWithNullEmpNo, personWithEmpNo)))
                .doesNotThrowAnyException();
    }

    @Test
    void exportZip_无证书员工_应正常导出() {
        PersonnelDTO personWithoutCerts = buildPersonnel(1L, "王五", "EMP003", List.of());

        assertThatCode(() -> zipExporter.exportZip(List.of(personWithoutCerts)))
                .doesNotThrowAnyException();
    }
}
