package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 业绩单行导入解析测试。
 *
 * <p>重点覆盖 CO-586 引入的合同协议附件必填校验：
 * 前端表单已要求合同协议附件必填，但后端导入原先不校验，
 * 导致前后端不一致。本测试确保 parseRow 对空值抛出明确异常。
 */
@ExtendWith(MockitoExtension.class)
class PerformanceRowImporterTest {

    @Mock
    private PerformanceRepository repository;

    @Mock
    private CreatePerformanceAppService createService;

    @Mock
    private UpdatePerformanceAppService updateService;

    private PerformanceRowImporter importer;

    @BeforeEach
    void setUp() {
        importer = new PerformanceRowImporter(repository, createService, updateService);
    }

    @Test
    void parseRow_contractAgreementBlank_throwsIllegalArgumentException() {
        Row row = createRow("合同A", "  ");

        assertThatThrownBy(() -> importer.parseRow(row, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合同协议附件文件名")
                .hasMessageContaining("不能为空");
    }

    @Test
    void parseRow_contractAgreementNull_throwsIllegalArgumentException() {
        Row row = createRow("合同A", null);

        assertThatThrownBy(() -> importer.parseRow(row, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合同协议附件文件名")
                .hasMessageContaining("不能为空");
    }

    @Test
    void parseRow_contractAgreementProvided_parsesSuccessfully() {
        Row row = createRow("合同A", "合同协议.pdf");

        var parsed = importer.parseRow(row, 2);

        assertThat(parsed.contractName()).isEqualTo("合同A");
        assertThat(parsed.attachmentFileNames())
                .hasSize(1)
                .anyMatch(a -> a.fileName().equals("合同协议.pdf") && a.fileType().equals("CONTRACT_AGREEMENT"));
        assertThat(parsed.command().attachments())
                .hasSize(1)
                .anyMatch(a -> a.fileName().equals("合同协议.pdf") && a.fileType().equals("CONTRACT_AGREEMENT"));
    }

    private Row createRow(String contractName, String contractAgreementFileName) {
        var wb = new XSSFWorkbook();
        var sheet = wb.createSheet();
        var row = sheet.createRow(0);
        row.createCell(0).setCellValue(contractName);
        // CO-586: 合同协议附件位于第 20 列（索引 19）
        if (contractAgreementFileName != null) {
            row.createCell(19).setCellValue(contractAgreementFileName);
        }
        return row;
    }
}
