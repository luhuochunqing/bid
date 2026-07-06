package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationImportRowResult;
import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §4.1.3.4 ImportQualificationAppService 单元测试
 * 覆盖：parse 跳过表头 / 必填校验 / 联系方式正则 / 日期顺序 / 附件命名 / 证书编号查重 / 空文件
 */
@ExtendWith(MockitoExtension.class)
class ImportQualificationAppServiceTest {

    @Mock private CreateQualificationAppService createQualificationAppService;
    @Mock private BusinessQualificationJpaRepository qualificationJpaRepository;

    @InjectMocks private ImportQualificationAppService importService;

    private static final String[] HEADERS = {
            "证书名称", "等级", "认证机构", "证书编号", "发证日期", "证书有效期",
            "代理机构", "代理机构联系人", "认证范围", "证书审核提醒", "附件文件名"
    };

    /** 构造包含表头 + 数据行的最小 xlsx，返回 multipart file */
    private MultipartFile buildExcel(String[][] body) throws Exception {
        return buildExcelInternal(body, null);
    }

    /**
     * 构造 xlsx，支持指定某些列为数字类型。
     * numericColumns 为列索引集合（0-based），对应单元格用 setCellValue(double) 写入数字。
     */
    private MultipartFile buildExcel(String[][] body, java.util.Set<Integer> numericColumns) throws Exception {
        return buildExcelInternal(body, numericColumns);
    }

    private MultipartFile buildExcelInternal(String[][] body, java.util.Set<Integer> numericColumns) throws Exception {
        return buildExcelInternal(body, numericColumns, null, null);
    }

    /**
     * 构造 xlsx，支持指定某些列为数字类型并设置单元格格式。
     */
    private MultipartFile buildExcelInternal(
            String[][] body,
            java.util.Set<Integer> numericColumns,
            Integer formatColumn,
            String formatPattern
    ) throws Exception {
        try (var wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("资质证书");
            Row hr = sh.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) hr.createCell(i).setCellValue(HEADERS[i]);
            var dataFormat = wb.createDataFormat();
            for (int r = 0; r < body.length; r++) {
                Row row = sh.createRow(r + 1);
                for (int c = 0; c < HEADERS.length; c++) {
                    String value = body[r][c] == null ? "" : body[r][c];
                    var cell = row.createCell(c);
                    if (numericColumns != null && numericColumns.contains(c)) {
                        try {
                            cell.setCellValue(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            cell.setCellValue(value);
                        }
                    } else {
                        cell.setCellValue(value);
                    }
                    if (formatColumn != null && formatColumn == c && formatPattern != null) {
                        var style = wb.createCellStyle();
                        style.setDataFormat(dataFormat.getFormat(formatPattern));
                        cell.setCellStyle(style);
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private MultipartFile buildExcelWithNumericFormat(String[][] body, int column, String formatPattern) throws Exception {
        return buildExcelInternal(body, java.util.Set.of(column), column, formatPattern);
    }

    @Test
    void importFromExcel_OnlyHeader_ShouldReturnEmpty() throws Exception {
        MultipartFile file = buildExcel(new String[0][]);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isZero();
        assertThat(summary.success()).isZero();
        assertThat(summary.failed()).isZero();
        assertThat(summary.results()).isEmpty();
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_OneValidRow_ShouldImport() throws Exception {
        String certNo = "IMP-VALID-1";
        MultipartFile file = buildExcel(new String[][]{{
                "E2E 导入测试", "FIRST", "中国计量认证中心", certNo,
                "2024-01-15", "2027-12-31", "代理A", "13800138000",
                "范围A", "提醒A", "QUAL_" + certNo + "_01_x.pdf"
        }});
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(false);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(createQualificationAppService, times(1)).create(any(QualificationUpsertCommand.class));
    }

    @Test
    void importFromExcel_DuplicateCertificateNo_ShouldSkipRow() throws Exception {
        String certNo = "IMP-DUP-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(true);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.success()).isZero();
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("已存在");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_MissingRequiredField_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-MISS-1";
        MultipartFile file = buildExcel(new String[][]{{
                "",                                // 证书名称空
                "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("不能为空");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_PlainTextAgencyContact_ShouldImport() throws Exception {
        String certNo = "IMP-CONT-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "张三", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(false);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(createQualificationAppService, times(1)).create(any(QualificationUpsertCommand.class));
    }

    @Test
    void importFromExcel_AgencyContactNameWithPhone_ShouldImport() throws Exception {
        String certNo = "IMP-CONT-2";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "张三 13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(false);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(createQualificationAppService, times(1)).create(any(QualificationUpsertCommand.class));
    }

    @Test
    void importFromExcel_ExpiryBeforeIssue_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-DATE-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2027-12-31", "2024-01-15",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("有效期");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_InvalidAttachmentName_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-FILE-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "wrong_filename.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("附件");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_MixedRows_ShouldAggregateSuccessAndFailure() throws Exception {
        String validCert = "IMP-MIX-A";
        String badCert = "IMP-MIX-B";
        MultipartFile file = buildExcel(new String[][]{
                {
                        "合法行", "FIRST", "科技局", validCert, "2024-01-15", "2027-12-31",
                        "代理A", "13800138000", "范围", "提醒", "QUAL_" + validCert + "_01_x.pdf"
                },
                {
                        "", "FIRST", "科技局", badCert, "2024-01-15", "2027-12-31",
                        "代理A", "13800138000", "范围", "提醒", "QUAL_" + badCert + "_01_x.pdf"
                }
        });
        when(qualificationJpaRepository.existsByCertificateNo(validCert)).thenReturn(false);
        

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        List<QualificationImportRowResult> results = summary.results();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).isSuccess()).isFalse();
    }

    @Test
    void importFromExcel_InvalidDateFormat_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-DT-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "not-a-date", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.failed()).isEqualTo(1);
        verify(createQualificationAppService, never()).create(any());
    }

    /**
     * CO-470 后续：证书编号在 Excel 中被识别为数字时，DataFormatter 会格式化为 20260630.0，
     * 导致文件名前缀 QUAL_20260630_ 与期望前缀 QUAL_20260630.0_ 不匹配。
     */
    @Test
    void importFromExcel_NumericCertificateNo_ShouldStillMatchAttachmentName() throws Exception {
        String certNo = "20260630";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_03_文件 2.docx"
        }}, java.util.Set.of(3));
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(false);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(createQualificationAppService, times(1)).create(any(QualificationUpsertCommand.class));
    }

    /**
     * CO-470 后续：证书编号单元格格式化为带两位小数时，DataFormatter 输出 20260630.00，
     * 附件文件名前缀 QUAL_20260630_ 与期望前缀 QUAL_20260630.00_ 不匹配。
     */
    @Test
    void importFromExcel_NumericCertificateNoWithTwoDecimals_ShouldImportSuccessfully() throws Exception {
        String certNo = "20260630";
        MultipartFile file = buildExcelWithNumericFormat(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_03_文件 2.docx"
        }}, 3, "0.00");
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(false);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(createQualificationAppService, times(1)).create(any(QualificationUpsertCommand.class));
    }

    /**
     * CO-470 后续：证书编号单元格格式化为千分位时，DataFormatter 输出 20,260,630，
     * 附件文件名前缀 QUAL_20260630_ 与期望前缀 QUAL_20,260,630_ 不匹配。
     */
    @Test
    void importFromExcel_NumericCertificateNoWithThousandsSeparator_ShouldImportSuccessfully() throws Exception {
        String certNo = "20260630";
        MultipartFile file = buildExcelWithNumericFormat(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_03_文件 2.docx"
        }}, 3, "#,##0");
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(false);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(createQualificationAppService, times(1)).create(any(QualificationUpsertCommand.class));
    }
}
