package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_ADDRESS;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_AREA;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_CONTACT;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_END_DATE;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_LESSOR;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_LESSEE;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_NAME;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_PROVINCE;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_REGION;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_START_DATE;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_TYPE;
import static com.xiyu.bid.warehouse.domain.WarehouseImportPolicy.COL_LEASE_CONTRACT_FILE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

class WarehouseImportPolicyTest {

    @Test
    @DisplayName("validateHeader 对完全匹配的表头返回空错误列表")
    void exactMatchHeaderIsValid() {
        List<String> errors = WarehouseImportPolicy.validateHeader(WarehouseImportPolicy.TEMPLATE_HEADERS);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("validateHeader 容忍全角括号、多余空格和末尾 * 标记")
    void normalizedHeaderIsValid() {
        String[] relaxed = new String[WarehouseImportPolicy.TEMPLATE_HEADERS.length];
        for (int i = 0; i < relaxed.length; i++) {
            relaxed[i] = WarehouseImportPolicy.TEMPLATE_HEADERS[i]
                    .replace("(", "（").replace(")", "）")
                    + "  ";
        }
        List<String> errors = WarehouseImportPolicy.validateHeader(relaxed);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("validateHeader 对列数不足返回错误")
    void tooFewColumnsReturnsError() {
        List<String> errors = WarehouseImportPolicy.validateHeader(new String[]{"仓库名称"});
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("列数不足");
    }

    @Test
    @DisplayName("validateHeader 对真正不匹配的列名返回错误")
    void mismatchedHeaderReturnsError() {
        String[] bad = new String[WarehouseImportPolicy.TEMPLATE_HEADERS.length];
        System.arraycopy(WarehouseImportPolicy.TEMPLATE_HEADERS, 0, bad, 0, bad.length);
        bad[0] = "完全错误的列名";
        List<String> errors = WarehouseImportPolicy.validateHeader(bad);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("第 1 列");
    }

    @Test
    @DisplayName("normalizeHeader 去除空格、统一全角符号、转小写、去末尾 *")
    void normalizeHeaderBehavior() {
        assertThat(WarehouseImportPolicy.normalizeHeader("  仓库名称*  ")).isEqualTo("仓库名称");
        assertThat(WarehouseImportPolicy.normalizeHeader("仓库名称")).isEqualTo("仓库名称");
        assertThat(WarehouseImportPolicy.normalizeHeader("仓库名称***")).isEqualTo("仓库名称");
        assertThat(WarehouseImportPolicy.normalizeHeader("是否有产权证（是/否）"))
                .isEqualTo("是否有产权证(是/否)");
        assertThat(WarehouseImportPolicy.normalizeHeader("AREA")).isEqualTo("area");
    }

    @Test
    @DisplayName("parseRow 支持多种常见日期格式")
    void parseRowSupportsVariousDateFormats() {
        assertDateParsed("2026-07-03", 2026, 7, 3);
        assertDateParsed("2026/07/03", 2026, 7, 3);
        assertDateParsed("2026.07.03", 2026, 7, 3);
        assertDateParsed("2026年7月3日", 2026, 7, 3);
        assertDateParsed("03-07-2026", 2026, 7, 3);
        assertDateParsed("03/07/2026", 2026, 7, 3);
    }

    @Test
    @DisplayName("parseRow 对非法日期格式返回格式错误")
    void parseRowRejectsInvalidDateFormat() {
        WarehouseImportRow row = parseRowWithDate("not-a-date");
        assertThat(row.startDate).isNull();
        assertThat(row.errors).anyMatch(e -> e.contains("开始时间")
                && e.contains("格式错误")
                && e.contains("YYYY-MM-DD")
                && e.contains("not-a-date"));
    }

    @Test
    @DisplayName("parseRow 对空日期返回不能为空错误")
    void parseRowRejectsEmptyDate() {
        WarehouseImportRow row = parseRowWithDate("");
        assertThat(row.startDate).isNull();
        assertThat(row.errors).anyMatch(e -> e.contains("开始时间") && e.contains("不能为空"));
    }

    private void assertDateParsed(String dateText, int year, int month, int day) {
        WarehouseImportRow row = parseRowWithDate(dateText);
        assertThat(row.errors).as("row errors for date %s: %s", dateText, row.errors).isEmpty();
        assertThat(row.startDate).as("startDate for %s", dateText).isNotNull();
        assertThat(row.startDate.getYear()).isEqualTo(year);
        assertThat(row.startDate.getMonthValue()).isEqualTo(month);
        assertThat(row.startDate.getDayOfMonth()).isEqualTo(day);
    }

    private WarehouseImportRow parseRowWithDate(String startDateText) {
        String[] cells = new String[WarehouseImportPolicy.EXPECTED_COL_COUNT];
        cells[COL_NAME] = "测试仓库";
        cells[COL_TYPE] = "自营";
        cells[COL_PROVINCE] = "上海市";
        cells[COL_ADDRESS] = "测试地址";
        cells[COL_AREA] = "100";
        cells[COL_REGION] = "华东";
        cells[COL_CONTACT] = "张三";
        cells[COL_START_DATE] = startDateText;
        cells[COL_END_DATE] = "2026-12-31";
        cells[COL_LESSOR] = "出租方A";
        cells[COL_LESSEE] = "承租方B";
        cells[COL_LEASE_CONTRACT_FILE_NAME] = "合同.pdf";
        return WarehouseImportPolicy.parseRow(2, cells);
    }

    @Test
    @DisplayName("parseRow 解析租赁合同文件名并生成标准附件名")
    void parseRowParsesLeaseContract() {
        WarehouseImportRow row = parseRowWithDate("2026-07-03");
        assertThat(row.hasLeaseContract).isTrue();
        assertThat(row.leaseContractFileName).isEqualTo("合同.pdf");
        assertThat(row.leaseContractFile).isEqualTo("合同.pdf");
        assertThat(row.leaseContractExpectedName).isEqualTo("WH_测试仓库_租赁合同.pdf");
    }

    @Test
    @DisplayName("parseRow 租赁合同文件名为空时 hasLeaseContract=false")
    void parseRowRejectsLeaseContractFileEmptyWhenYes() {
        String[] cells = new String[WarehouseImportPolicy.EXPECTED_COL_COUNT];
        cells[COL_NAME] = "测试仓库";
        cells[COL_TYPE] = "自营";
        cells[COL_PROVINCE] = "上海市";
        cells[COL_ADDRESS] = "测试地址";
        cells[COL_AREA] = "100";
        cells[COL_REGION] = "华东";
        cells[COL_CONTACT] = "张三";
        cells[COL_START_DATE] = "2026-07-03";
        cells[COL_END_DATE] = "2026-12-31";
        cells[COL_LESSOR] = "出租方A";
        cells[COL_LESSEE] = "承租方B";
        cells[COL_LEASE_CONTRACT_FILE_NAME] = "";
        WarehouseImportRow row = WarehouseImportPolicy.parseRow(2, cells);
        assertThat(row.hasLeaseContract).isFalse();
        assertThat(row.leaseContractExpectedName).isNullOrEmpty();
        assertThat(row.errors).noneMatch(e -> e.contains("租赁合同"));
    }

    @Test
    @DisplayName("TEMPLATE_HEADERS 包含租赁合同文件名列，不再包含开关和附件列")
    void templateHeadersContainsLeaseContract() {
        assertThat(WarehouseImportPolicy.TEMPLATE_HEADERS).contains("租赁合同文件名");
        assertThat(WarehouseImportPolicy.TEMPLATE_HEADERS).doesNotContain("是否有租赁合同", "租赁合同附件");
        assertThat(WarehouseImportPolicy.EXPECTED_COL_COUNT).isEqualTo(23);
    }

    @Test
    @DisplayName("parseRow 对非法省份返回白名单错误")
    void parseRowRejectsInvalidProvince() {
        String[] cells = new String[WarehouseImportPolicy.EXPECTED_COL_COUNT];
        cells[COL_NAME] = "测试仓库";
        cells[COL_TYPE] = "自营";
        cells[COL_PROVINCE] = "华东";
        cells[COL_ADDRESS] = "测试地址";
        cells[COL_AREA] = "100";
        cells[COL_REGION] = "华东";
        cells[COL_CONTACT] = "张三";
        cells[COL_START_DATE] = "2026-07-03";
        cells[COL_END_DATE] = "2026-12-31";
        cells[COL_LESSOR] = "出租方A";
        cells[COL_LESSEE] = "承租方B";
        cells[COL_LEASE_CONTRACT_FILE_NAME] = "合同.pdf";
        WarehouseImportRow row = WarehouseImportPolicy.parseRow(2, cells);
        assertThat(row.errors).anyMatch(e -> e.contains("省份") && e.contains("格式错误"));
    }

    @Test
    @DisplayName("parseRow 接受 34 个省级行政区之一的省份")
    void parseRowAcceptsValidProvince() {
        String[] cells = new String[WarehouseImportPolicy.EXPECTED_COL_COUNT];
        cells[COL_NAME] = "测试仓库";
        cells[COL_TYPE] = "自营";
        cells[COL_PROVINCE] = "北京市";
        cells[COL_ADDRESS] = "测试地址";
        cells[COL_AREA] = "100";
        cells[COL_REGION] = "华东";
        cells[COL_CONTACT] = "张三";
        cells[COL_START_DATE] = "2026-07-03";
        cells[COL_END_DATE] = "2026-12-31";
        cells[COL_LESSOR] = "出租方A";
        cells[COL_LESSEE] = "承租方B";
        cells[COL_LEASE_CONTRACT_FILE_NAME] = "合同.pdf";
        WarehouseImportRow row = WarehouseImportPolicy.parseRow(2, cells);
        assertThat(row.errors).noneMatch(e -> e.contains("省份"));
        assertThat(row.province).isEqualTo("北京市");
    }
}
