package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WarehouseLedgerExportPolicyTest {

    @Mock
    private WarehouseReadModel wh;

    private void stubBasicFields() {
        lenient().when(wh.getName()).thenReturn("上海仓");
        lenient().when(wh.getType()).thenReturn(WarehouseType.SELF_OPERATED);
        lenient().when(wh.getRegion()).thenReturn("华东");
        lenient().when(wh.getProvince()).thenReturn("上海");
        lenient().when(wh.getAddress()).thenReturn("浦东新区XX路100号");
        lenient().when(wh.getArea()).thenReturn(new java.math.BigDecimal("5000.00"));
        lenient().when(wh.getContactPerson()).thenReturn("张三");
        lenient().when(wh.getRemarks()).thenReturn("主仓库");
        lenient().when(wh.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        lenient().when(wh.getEndDate()).thenReturn(LocalDate.of(2027, 1, 1));
        lenient().when(wh.getLessor()).thenReturn("甲方");
        lenient().when(wh.getLessee()).thenReturn("乙方");
        lenient().when(wh.getHasPropertyCert()).thenReturn(true);
        lenient().when(wh.getHasInvoice()).thenReturn(false);
        lenient().when(wh.getHasPhotos()).thenReturn(true);
        lenient().when(wh.getClosePlan()).thenReturn("");
        lenient().when(wh.getStatus()).thenReturn(WarehouseStatus.IN_USE);
        lenient().when(wh.getInvoicePeriodStart()).thenReturn(LocalDate.of(2026, 1, 1));
        lenient().when(wh.getInvoicePeriodEnd()).thenReturn(LocalDate.of(2026, 12, 31));
        lenient().when(wh.getCreatedBy()).thenReturn(1L);
        lenient().when(wh.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private Map<Long, String> usernameMap() {
        return Map.of(1L, "管理员");
    }

    @Test
    void buildRows_WithEmptyEntities_ShouldReturnEmptyList() {
        List<String[]> rows = WarehouseLedgerExportPolicy.buildRows(
                List.of(), Set.of(WarehouseLedgerExportPolicy.Section.BASIC), Map.of());
        assertThat(rows).isEmpty();
    }

    @Test
    void buildRows_WithFullSections_ShouldPopulateAllColumns() {
        stubBasicFields();

        List<String[]> rows = WarehouseLedgerExportPolicy.buildRows(
                List.of(wh),
                Set.of(WarehouseLedgerExportPolicy.Section.BASIC,
                       WarehouseLedgerExportPolicy.Section.LEASE,
                       WarehouseLedgerExportPolicy.Section.DOC,
                       WarehouseLedgerExportPolicy.Section.META), usernameMap());
        assertThat(rows).hasSize(1);
        String[] row = rows.get(0);

        // index column
        assertThat(row[WarehouseLedgerExportPolicy.COL_INDEX]).isEqualTo("1");
        // BASIC columns
        assertThat(row[WarehouseLedgerExportPolicy.COL_NAME]).isEqualTo("上海仓");
        assertThat(row[WarehouseLedgerExportPolicy.COL_TYPE]).isEqualTo("自营");
        assertThat(row[WarehouseLedgerExportPolicy.COL_REGION]).isEqualTo("华东");
        assertThat(row[WarehouseLedgerExportPolicy.COL_PROVINCE]).isEqualTo("上海");
        assertThat(row[WarehouseLedgerExportPolicy.COL_ADDRESS]).isEqualTo("浦东新区XX路100号");
        // LEASE columns
        assertThat(row[WarehouseLedgerExportPolicy.COL_START]).isEqualTo("2026-01-01");
        assertThat(row[WarehouseLedgerExportPolicy.COL_END]).isEqualTo("2027-01-01");
        assertThat(row[WarehouseLedgerExportPolicy.COL_LESSOR]).isEqualTo("甲方");
        assertThat(row[WarehouseLedgerExportPolicy.COL_LESSEE]).isEqualTo("乙方");
        // DOC columns
        assertThat(row[WarehouseLedgerExportPolicy.COL_HAS_CERT]).isEqualTo("是");
        assertThat(row[WarehouseLedgerExportPolicy.COL_HAS_INVOICE]).isEqualTo("否");
        assertThat(row[WarehouseLedgerExportPolicy.COL_HAS_PHOTOS]).isEqualTo("是");
        // STATUS column
        assertThat(row[WarehouseLedgerExportPolicy.COL_STATUS]).isEqualTo("使用中");
        // META columns
        assertThat(row[WarehouseLedgerExportPolicy.COL_CREATED_AT]).isEqualTo("2026-01-01T00:00");
        assertThat(row[WarehouseLedgerExportPolicy.COL_CREATED_BY]).isEqualTo("管理员");
    }

    @Test
    void buildRows_WithBasicOnly_ShouldOnlyPopulateIndexAndBasicColumns() {
        stubBasicFields();

        List<String[]> rows = WarehouseLedgerExportPolicy.buildRows(
                List.of(wh), Set.of(WarehouseLedgerExportPolicy.Section.BASIC), Map.of());
        String[] row = rows.get(0);

        // Index + BASIC should be non-empty
        assertThat(row[WarehouseLedgerExportPolicy.COL_INDEX]).isNotEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_NAME]).isNotEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_TYPE]).isNotEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_REMARKS]).isNotEmpty();
        // LEASE should be empty
        assertThat(row[WarehouseLedgerExportPolicy.COL_START]).isEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_END]).isEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_LESSOR]).isEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_LESSEE]).isEmpty();
        // DOC should be empty
        assertThat(row[WarehouseLedgerExportPolicy.COL_HAS_CERT]).isEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_HAS_INVOICE]).isEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_HAS_PHOTOS]).isEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_STATUS]).isEmpty();
        // META should be empty
        assertThat(row[WarehouseLedgerExportPolicy.COL_CREATED_AT]).isEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_CREATED_BY]).isEmpty();
    }

    @Test
    void buildRows_WithLeaseAndDocOnly_ShouldNotIncludeBasic() {
        stubBasicFields();

        List<String[]> rows = WarehouseLedgerExportPolicy.buildRows(
                List.of(wh),
                Set.of(WarehouseLedgerExportPolicy.Section.LEASE,
                       WarehouseLedgerExportPolicy.Section.DOC), Map.of());
        String[] row = rows.get(0);

        assertThat(row[WarehouseLedgerExportPolicy.COL_INDEX]).isNotEmpty();
        // BASIC should be empty
        assertThat(row[WarehouseLedgerExportPolicy.COL_NAME]).isEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_TYPE]).isEmpty();
        // LEASE should be populated
        assertThat(row[WarehouseLedgerExportPolicy.COL_START]).isNotEmpty();
        assertThat(row[WarehouseLedgerExportPolicy.COL_END]).isNotEmpty();
        // DOC should be populated
        assertThat(row[WarehouseLedgerExportPolicy.COL_HAS_CERT]).isNotEmpty();
    }

    @Test
    void getHeaders_WithFullSections_ShouldReturnAllHeaders() {
        String[] headers = WarehouseLedgerExportPolicy.getHeaders(
                Set.of(WarehouseLedgerExportPolicy.Section.BASIC,
                       WarehouseLedgerExportPolicy.Section.LEASE,
                       WarehouseLedgerExportPolicy.Section.DOC,
                       WarehouseLedgerExportPolicy.Section.META));
        assertThat(headers).hasSize(WarehouseLedgerExportPolicy.HEADER_COUNT);
        assertThat(headers[WarehouseLedgerExportPolicy.COL_INDEX]).isNotEmpty();
        assertThat(headers[WarehouseLedgerExportPolicy.COL_NAME]).isNotEmpty();
        assertThat(headers[WarehouseLedgerExportPolicy.COL_START]).isNotEmpty();
        assertThat(headers[WarehouseLedgerExportPolicy.COL_HAS_CERT]).isNotEmpty();
        assertThat(headers[WarehouseLedgerExportPolicy.COL_CREATED_AT]).isEqualTo("创建时间");
        assertThat(headers[WarehouseLedgerExportPolicy.COL_CREATED_BY]).isEqualTo("创建人");
    }

    @Test
    void getHeaders_WithBasicOnly_ShouldOnlyContainBasicHeaders() {
        String[] headers = WarehouseLedgerExportPolicy.getHeaders(
                Set.of(WarehouseLedgerExportPolicy.Section.BASIC));
        assertThat(headers[WarehouseLedgerExportPolicy.COL_INDEX]).isNotEmpty();
        assertThat(headers[WarehouseLedgerExportPolicy.COL_NAME]).isNotEmpty();
        assertThat(headers[WarehouseLedgerExportPolicy.COL_START]).isEmpty();
        assertThat(headers[WarehouseLedgerExportPolicy.COL_HAS_CERT]).isEmpty();
        assertThat(headers[WarehouseLedgerExportPolicy.COL_CREATED_AT]).isEmpty();
    }

    @Test
    void getFullHeaders_ShouldReturnCloneOfAllHeaders() {
        String[] headers = WarehouseLedgerExportPolicy.getFullHeaders();
        assertThat(headers).hasSize(WarehouseLedgerExportPolicy.HEADER_COUNT);
        // Verify first and last headers
        assertThat(headers[WarehouseLedgerExportPolicy.COL_INDEX]).isEqualTo("序号");
        assertThat(headers[WarehouseLedgerExportPolicy.COL_CREATED_BY]).isEqualTo("创建人");
    }

    @Test
    void formatInvoicePeriod_WithBothDates_ShouldReturnFormattedRange() {
        stubBasicFields();

        String[] row = WarehouseLedgerExportPolicy.buildRows(
                List.of(wh), Set.of(WarehouseLedgerExportPolicy.Section.LEASE), Map.of()).get(0);
        assertThat(row[WarehouseLedgerExportPolicy.COL_INVOICE_PERIOD]).isEqualTo("2026-01-01 ~ 2026-12-31");
    }

    @Test
    void formatInvoicePeriod_WithNullBoth_ShouldReturnEmpty() {
        lenient().when(wh.getInvoicePeriodStart()).thenReturn(null);
        lenient().when(wh.getInvoicePeriodEnd()).thenReturn(null);
        lenient().when(wh.getInvoicePeriod()).thenReturn(null);
        lenient().when(wh.getName()).thenReturn("测试仓");
        lenient().when(wh.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        lenient().when(wh.getEndDate()).thenReturn(LocalDate.of(2027, 1, 1));
        lenient().when(wh.getHasPropertyCert()).thenReturn(false);
        lenient().when(wh.getHasInvoice()).thenReturn(false);
        lenient().when(wh.getHasPhotos()).thenReturn(false);
        lenient().when(wh.getStatus()).thenReturn(WarehouseStatus.IN_USE);

        String[] row = WarehouseLedgerExportPolicy.buildRows(
                List.of(wh), Set.of(WarehouseLedgerExportPolicy.Section.LEASE), Map.of()).get(0);
        assertThat(row[WarehouseLedgerExportPolicy.COL_INVOICE_PERIOD]).isEmpty();
    }

    @Test
    void displayName_ShouldReturnChineseNameForEnums() {
        assertThat(WarehouseLedgerExportPolicy.getFullHeaders()).isNotEmpty();
    }
}
