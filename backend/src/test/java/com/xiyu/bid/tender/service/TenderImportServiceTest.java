package com.xiyu.bid.tender.service;

import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderImportResultDTO;
import com.xiyu.bid.tender.dto.TenderRequest;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderImportServiceTest {

    @Mock
    private TenderCommandService tenderCommandService;

    @Mock
    private TenderMapper tenderMapper;

    private TenderExcelCellReader cellReader;

    @Mock
    private Validator validator;

    private TenderImportTemplateBuilder templateBuilder;
    private TenderImportService service;

    @BeforeEach
    void setUp() {
        templateBuilder = new TenderImportTemplateBuilder();
        cellReader = new TenderExcelCellReader();
        service = new TenderImportService(tenderCommandService, tenderMapper, templateBuilder, cellReader, validator);
    }

    @Test
    @DisplayName("generateTemplate 输出包含「标讯导入」与「字典参考」两张 sheet")
    void generateTemplateProducesTwoSheets() throws Exception {
        byte[] bytes = service.generateTemplate();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("标讯导入");
            assertThat(workbook.getSheetName(1)).isEqualTo("字典参考");
            Row header = workbook.getSheetAt(0).getRow(0);
            for (int i = 0; i < TenderImportService.HEADERS.length; i++) {
                assertThat(header.getCell(i).getStringCellValue()).isEqualTo(TenderImportService.HEADERS[i]);
            }
        }
    }

    @Test
    @DisplayName("空文件应当被拒绝并提示")
    void emptyFileIsRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[0]);
        assertThatThrownBy(() -> service.importFromExcel(empty, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请上传");
    }

    @Test
    @DisplayName("非 .xlsx 扩展名应当被拒绝")
    void nonXlsxExtensionIsRejected() {
        MockMultipartFile csv = new MockMultipartFile(
                "file", "tenders.csv", "text/csv", "a,b,c".getBytes());
        assertThatThrownBy(() -> service.importFromExcel(csv, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 .xlsx");
    }

    @Test
    @DisplayName("表头错位应当抛出「模板表头不匹配」")
    void headerMismatchIsRejected() throws Exception {
        byte[] bytes = buildWorkbookWithHeaders(new String[]{"错误表头"}, new String[][]{});
        MockMultipartFile bad = new MockMultipartFile(
                "file", "bad.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        assertThatThrownBy(() -> service.importFromExcel(bad, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板表头不匹配");
    }

    @Test
    @DisplayName("任一行非法应当全量回滚，不调用 createTender")
    void anyInvalidRowTriggersRollback() throws Exception {
        when(validator.validate(any(TenderRequest.class))).thenReturn(Collections.emptySet());

        // 客户类型不在白名单 -> 校验失败
        String[] row = exampleRow();
        row[13] = "未知类型";
        byte[] bytes = buildWorkbookWithHeaders(TenderImportService.HEADERS, new String[][]{row});

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        assertThatThrownBy(() -> service.importFromExcel(file, 1L))
                .isInstanceOf(TenderImportRollbackException.class)
                .satisfies(ex -> {
                    TenderImportResultDTO result = ((TenderImportRollbackException) ex).getResult();
                    assertThat(result.getTotalRows()).isEqualTo(1);
                    assertThat(result.getSuccessCount()).isZero();
                    assertThat(result.getFailureCount()).isEqualTo(1);
                    assertThat(result.getErrors()).hasSize(1);
                    assertThat(result.getErrors().get(0).field()).isEqualTo("customerType");
                });

        verify(tenderCommandService, never()).createTender(any(), any());
    }

    @Test
    @DisplayName("缺少必填字段（报名截止时间）应当全量回滚")
    void missingRequiredFieldTriggersRollback() throws Exception {
        when(validator.validate(any(TenderRequest.class))).thenReturn(Collections.emptySet());

        // 报名截止时间为空 -> 必填校验失败
        String[] row = exampleRow();
        row[3] = null;
        byte[] bytes = buildWorkbookWithHeaders(TenderImportService.HEADERS, new String[][]{row});

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        assertThatThrownBy(() -> service.importFromExcel(file, 1L))
                .isInstanceOf(TenderImportRollbackException.class)
                .satisfies(ex -> {
                    TenderImportResultDTO result = ((TenderImportRollbackException) ex).getResult();
                    assertThat(result.getTotalRows()).isEqualTo(1);
                    assertThat(result.getSuccessCount()).isZero();
                    assertThat(result.getFailureCount()).isEqualTo(1);
                    assertThat(result.getErrors().get(0).field()).isEqualTo("registrationDeadline");
                    assertThat(result.getErrors().get(0).message()).contains("不能为空");
                });

        verify(tenderCommandService, never()).createTender(any(), any());
    }

    @Test
    @DisplayName("非法项目类型应当全量回滚")
    void invalidProjectTypeTriggersRollback() throws Exception {
        when(validator.validate(any(TenderRequest.class))).thenReturn(Collections.emptySet());

        // 项目类型不在白名单 -> 校验失败
        String[] row = exampleRow();
        row[15] = "非法类型";
        byte[] bytes = buildWorkbookWithHeaders(TenderImportService.HEADERS, new String[][]{row});

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        assertThatThrownBy(() -> service.importFromExcel(file, 1L))
                .isInstanceOf(TenderImportRollbackException.class)
                .satisfies(ex -> {
                    TenderImportResultDTO result = ((TenderImportRollbackException) ex).getResult();
                    assertThat(result.getTotalRows()).isEqualTo(1);
                    assertThat(result.getSuccessCount()).isZero();
                    assertThat(result.getFailureCount()).isEqualTo(1);
                    assertThat(result.getErrors().get(0).field()).isEqualTo("projectType");
                });

        verify(tenderCommandService, never()).createTender(any(), any());
    }

    @Test
    @DisplayName("总部所在地为纯省名（如\"北京\"）应当全量回滚")
    void plainProvinceNameRegionTriggersRollback() throws Exception {
        when(validator.validate(any(TenderRequest.class))).thenReturn(Collections.emptySet());

        String[] row = exampleRow();
        row[2] = "北京";
        byte[] bytes = buildWorkbookWithHeaders(TenderImportService.HEADERS, new String[][]{row});

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        assertThatThrownBy(() -> service.importFromExcel(file, 1L))
                .isInstanceOf(TenderImportRollbackException.class)
                .satisfies(ex -> {
                    TenderImportResultDTO result = ((TenderImportRollbackException) ex).getResult();
                    assertThat(result.getErrors().get(0).field()).isEqualTo("region");
                    assertThat(result.getErrors().get(0).message()).contains("一级+二级");
                });

        verify(tenderCommandService, never()).createTender(any(), any());
    }

    @Test
    @DisplayName("总部所在地为省+市格式（如\"广东省深圳市\"）应当通过校验")
    void provincePlusCityRegionIsAccepted() throws Exception {
        when(validator.validate(any(TenderRequest.class))).thenReturn(Collections.emptySet());
        when(tenderMapper.toDTO(any(TenderRequest.class))).thenReturn(new TenderDTO());

        String[] row = exampleRow();
        row[2] = "广东省深圳市";
        byte[] bytes = buildWorkbookWithHeaders(TenderImportService.HEADERS, new String[][]{row});

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        TenderImportResultDTO result = service.importFromExcel(file, 1L);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(tenderCommandService, times(1)).createTender(any(), any());
    }

    @Test
    @DisplayName("REGIONS 白名单为省+市格式，不含纯省名")
    void regionsWhitelistUsesProvincePlusCityFormat() {
        assertThat(TenderImportService.REGIONS).contains("北京市", "广东省深圳市");
        assertThat(TenderImportService.REGIONS).doesNotContain("北京", "广东");
    }

    @Test
    @DisplayName("模板表头共 18 列且顺序与蓝图一致")
    void templateHasEighteenColumns() {
        assertThat(TenderImportService.HEADERS).hasSize(18);
        assertThat(TenderImportService.HEADERS[0]).isEqualTo("项目名称*");
        assertThat(TenderImportService.HEADERS[15]).isEqualTo("项目类型");
        assertThat(TenderImportService.HEADERS[17]).isEqualTo("标讯描述");
    }

    @Test
    @DisplayName("normalizeHeader 忽略空格、全角符号、大小写和末尾 * 标记")
    void normalizeHeaderHandlesCommonVariants() {
        assertThat(TenderImportService.normalizeHeader("  项目名称*  ")).isEqualTo("项目名称");
        assertThat(TenderImportService.normalizeHeader("项目名称")).isEqualTo("项目名称");
        assertThat(TenderImportService.normalizeHeader("项目名称***")).isEqualTo("项目名称");
        assertThat(TenderImportService.normalizeHeader("项目 名称")).isEqualTo("项目 名称");
        assertThat(TenderImportService.normalizeHeader("联系人1（手机）")).isEqualTo("联系人1(手机)");
        assertThat(TenderImportService.normalizeHeader("CONTACT_NAME")).isEqualTo("contact_name");
    }

    @Test
    @DisplayName("表头含全角括号或多余空格应被容忍")
    void headerWithFullWidthCharsIsAccepted() throws Exception {
        when(validator.validate(any(TenderRequest.class))).thenReturn(Collections.emptySet());
        when(tenderMapper.toDTO(any(TenderRequest.class))).thenReturn(new TenderDTO());

        String[] relaxedHeaders = new String[TenderImportService.HEADERS.length];
        for (int i = 0; i < relaxedHeaders.length; i++) {
            relaxedHeaders[i] = TenderImportService.HEADERS[i].replace("(", "（").replace(")", "）") + " ";
        }
        byte[] bytes = buildWorkbookWithHeaders(relaxedHeaders, new String[][]{exampleRow()});
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        TenderImportResultDTO result = service.importFromExcel(file, 1L);
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("全部行合法时应当依次创建并返回成功汇总")
    void allValidRowsAreImported() throws Exception {
        when(validator.validate(any(TenderRequest.class))).thenReturn(Collections.emptySet());
        when(tenderMapper.toDTO(any(TenderRequest.class))).thenReturn(new TenderDTO());

        byte[] bytes = buildWorkbookWithHeaders(TenderImportService.HEADERS, new String[][]{
                exampleRow(),
                exampleRow()
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        TenderImportResultDTO result = service.importFromExcel(file, 1L);
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getErrors()).isEmpty();
        verify(tenderCommandService, times(2)).createTender(any(), any());
    }

    @Test
    @DisplayName("模板字典 sheet 使用与前端 constants.js 对齐的最新字典值")
    void templateDictionarySheetUsesLatestOptions() throws Exception {
        byte[] bytes = service.generateTemplate();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet dict = workbook.getSheet("字典参考");
            assertThat(dict).isNotNull();

            List<String> customerTypes = readColumn(dict, 1);
            List<String> projectTypes = readColumn(dict, 3);

            // 客户类型对齐前端 constants.js CUSTOMER_TYPE_OPTIONS
            assertThat(customerTypes).contains(
                    "政府机关/事业单位/高校", "央企", "地方国企", "民企", "港澳台及外企");
            assertThat(customerTypes).doesNotContain("央企集团", "国有集团", "KA 客户");

            // 项目类型对齐前端 constants.js PROJECT_TYPE_OPTIONS
            assertThat(projectTypes).contains("工业品", "办公", "综合", "集采", "其他");
            assertThat(projectTypes).doesNotContain("货物类", "工程类", "服务类");
        }
    }

    @Test
    @DisplayName("字典 sheet 地区列表头描述与示例和校验提示口径一致（一级+二级格式）")
    void dictionarySheetRegionHeaderMatchesExampleAndValidationHint() throws Exception {
        byte[] bytes = service.generateTemplate();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet dict = workbook.getSheet("字典参考");
            assertThat(dict).isNotNull();
            String regionHeader = dict.getRow(0).getCell(0).getStringCellValue();
            // 表头应明确一级+二级格式，与校验提示口径一致
            assertThat(regionHeader).contains("一级+二级");
            assertThat(regionHeader).doesNotContain("市-市");
        }
    }

    @Test
    @DisplayName("字典 sheet 地区列只列推荐的一级+二级格式，不列兼容的旧格式")
    void dictionarySheetRegionColumnExcludesMunicipalityOnlyName() throws Exception {
        byte[] bytes = service.generateTemplate();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet dict = workbook.getSheet("字典参考");
            assertThat(dict).isNotNull();
            List<String> regions = readColumn(dict, 0);

            // 直辖市推荐格式"一级+二级拼接"应存在
            assertThat(regions).contains("北京市北京市", "天津市天津市", "上海市上海市", "重庆市重庆市");
            // 直辖市兼容的旧格式（仅市、市-市）不应在字典 sheet 展示
            assertThat(regions).doesNotContain("北京市", "天津市", "上海市", "重庆市");
            assertThat(regions).doesNotContain("北京市-北京市", "天津市-天津市", "上海市-上海市", "重庆市-重庆市");
            // 港澳台推荐格式"一级+二级拼接"应存在，旧单名不展示
            assertThat(regions).contains("台湾省台北市", "香港特别行政区中西区", "澳门特别行政区花地玛堂区");
            assertThat(regions).doesNotContain("台湾省", "香港特别行政区", "澳门特别行政区");
            // 普通省+市格式仍保留
            assertThat(regions).contains("广东省深圳市");
        }
    }

    private List<String> readColumn(Sheet sheet, int colIndex) {
        List<String> values = new ArrayList<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell cell = row.getCell(colIndex);
            if (cell != null) {
                values.add(cell.getStringCellValue());
            }
        }
        return values;
    }

    private String[] exampleRow() {
        return new String[]{
                "测试项目名称",
                "测试招标主体",
                "北京市-北京市",
                "2026-12-31 17:00",
                "2026-12-25 09:30",
                "张三",
                "13800138000",
                "010-12345678",
                "zhangsan@example.com",
                "李四",
                "13900139000",
                "021-87654321",
                "lisi@example.com",
                "央企",
                "A",
                "工业品",
                "政府采购网",
                "示例标讯描述"
        };
    }

    private byte[] buildWorkbookWithHeaders(String[] headers, String[][] dataRows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("标讯导入");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                String[] values = dataRows[r];
                for (int c = 0; c < values.length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(values[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
