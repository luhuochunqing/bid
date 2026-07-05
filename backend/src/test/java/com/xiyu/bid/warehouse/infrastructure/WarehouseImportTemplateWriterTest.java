package com.xiyu.bid.warehouse.infrastructure;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WarehouseImportTemplateWriter 单测。
 *
 * 重点覆盖：模板中应当做成下拉框的列（仓库类型 / 是否有产权证 / 是否有发票 /
 * 是否有仓库照片 / 是否有租赁合同）确实带 Excel DataValidation。
 *
 * 修复前模板只写了文字 hint（如"自营 或 云仓"），用户仍可自由填写导致导入失败。
 */
class WarehouseImportTemplateWriterTest {

    private final WarehouseImportTemplateWriter writer = new WarehouseImportTemplateWriter();

    @Test
    @DisplayName("write 返回的 Excel 可被 POI 正常打开，且表头数量与策略定义一致")
    void write_returnsValidXlsxWithExpectedHeaderCount() throws IOException {
        byte[] bytes = writer.write();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("仓库导入模板");
            assertThat(sheet).isNotNull();
            // 表头行 + 提示行 = 至少 2 行
            assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(1);
            // 表头列数 = 24（WarehouseImportPolicy.EXPECTED_COL_COUNT）
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 24);
        }
    }

    @Test
    @DisplayName("模板包含 7 个下拉框：仓库类型 + 省份 + 所属区域 + 4 个是否类字段")
    void write_containsDropDownValidationsForEnumColumns() throws IOException {
        byte[] bytes = writer.write();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("仓库导入模板");
            List<? extends DataValidation> validations = sheet.getDataValidations();

            // 期望 7 个下拉框，分别在第 1/2/5/15/17/19/21 列（0-based）
            // COL_TYPE=1（仓库类型：自营/云仓）
            // COL_PROVINCE=2（所在省份：34 个省级行政区）
            // COL_REGION=5（所属区域：华北/东北/华东/华中/华南/西北/西南）
            // COL_HAS_PROPERTY_CERT=15（是否有产权证：是/否）
            // COL_HAS_INVOICE=17（是否有发票：是/否）
            // COL_HAS_PHOTOS=19（是否有仓库照片：是/否）
            // COL_HAS_LEASE_CONTRACT=21（是否有租赁合同：是/否）
            assertThat(validations).hasSize(7);

            List<Integer> validatedCols = validations.stream()
                    .map(v -> v.getRegions().getCellRangeAddresses())
                    .flatMap(java.util.Arrays::stream)
                    .mapToInt(CellRangeAddress::getFirstColumn)
                    .sorted()
                    .boxed()
                    .toList();
            assertThat(validatedCols).containsExactlyInAnyOrder(1, 2, 5, 15, 17, 19, 21);
        }
    }

    @Test
    @DisplayName("所属区域下拉框选项为 7 大区域 [华北, 东北, 华东, 华中, 华南, 西北, 西南]")
    void write_regionColumnDropDownContainsSevenRegions() throws IOException {
        byte[] bytes = writer.write();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("仓库导入模板");
            DataValidation regionValidation = findValidationForColumn(sheet, 5);
            assertThat(regionValidation).isNotNull();
            String[] options = regionValidation.getValidationConstraint().getExplicitListValues();
            assertThat(options).containsExactlyInAnyOrder("华北", "东北", "华东", "华中", "华南", "西北", "西南");
        }
    }

    @Test
    @DisplayName("所在省份下拉框选项为 34 个省级行政区")
    void write_provinceColumnDropDownContainsProvinces() throws IOException {
        byte[] bytes = writer.write();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("仓库导入模板");
            DataValidation provinceValidation = findValidationForColumn(sheet, 2);
            assertThat(provinceValidation).isNotNull();
            String[] options = provinceValidation.getValidationConstraint().getExplicitListValues();
            assertThat(options).containsExactlyInAnyOrder(
                    "北京市", "天津市", "上海市", "重庆市",
                    "河北省", "山西省", "辽宁省", "吉林省", "黑龙江省",
                    "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省",
                    "河南省", "湖北省", "湖南省", "广东省", "海南省",
                    "四川省", "贵州省", "云南省", "陕西省", "甘肃省", "青海省",
                    "台湾省",
                    "内蒙古自治区", "广西壮族自治区", "西藏自治区", "宁夏回族自治区", "新疆维吾尔自治区",
                    "香港特别行政区", "澳门特别行政区"
            );
        }
    }

    @Test
    @DisplayName("仓库类型下拉框选项为 [自营, 云仓]")
    void write_typeColumnDropDownContainsSelfOperatedAndCloud() throws IOException {
        byte[] bytes = writer.write();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("仓库导入模板");
            DataValidation typeValidation = findValidationForColumn(sheet, 1);
            assertThat(typeValidation).isNotNull();
            // POI 显式列表约束的 getCachedListValues 在不同版本接口略有差异，
            // 用 getValidationConstraint().getExplicitListValues 兜底取值
            String[] options = typeValidation.getValidationConstraint().getExplicitListValues();
            assertThat(options).containsExactlyInAnyOrder("自营", "云仓");
        }
    }

    @Test
    @DisplayName("是否类下拉框选项为 [是, 否]")
    void write_yesNoColumnsDropDownContainsYesAndNo() throws IOException {
        byte[] bytes = writer.write();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("仓库导入模板");
            for (int col : new int[]{15, 17, 19, 21}) {
                DataValidation v = findValidationForColumn(sheet, col);
                assertThat(v)
                        .as("第 %d 列应存在下拉框", col)
                        .isNotNull();
                String[] options = v.getValidationConstraint().getExplicitListValues();
                assertThat(options).containsExactlyInAnyOrder("是", "否");
            }
        }
    }

    private DataValidation findValidationForColumn(Sheet sheet, int col) {
        return sheet.getDataValidations().stream()
                .filter(v -> java.util.Arrays.stream(v.getRegions().getCellRangeAddresses())
                        .anyMatch(r -> r.getFirstColumn() == col))
                .findFirst()
                .orElse(null);
    }
}
