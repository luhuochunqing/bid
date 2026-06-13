package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WarehouseExportPolicyTest {

    @Mock
    private WarehouseReadModel wh;

    @Mock
    private WarehouseAttachmentReadModel certAttach;

    @Mock
    private WarehouseAttachmentReadModel invoiceAttach;

    @Mock
    private WarehouseAttachmentReadModel photoAttach;

    private void stubBasicFields() {
        lenient().when(wh.getId()).thenReturn(1L);
        lenient().when(wh.getName()).thenReturn("上海仓");
        lenient().when(wh.getType()).thenReturn(WarehouseType.SELF_OPERATED);
        lenient().when(wh.getProvince()).thenReturn("上海");
        lenient().when(wh.getRegion()).thenReturn("华东");
        lenient().when(wh.getAddress()).thenReturn("浦东新区XX路100号");
        lenient().when(wh.getArea()).thenReturn(new BigDecimal("5000.00"));
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
        lenient().when(wh.getCertRemarks()).thenReturn("证书备注");
        lenient().when(wh.getCreatedBy()).thenReturn(1L);
        lenient().when(wh.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        lenient().when(wh.getUpdatedBy()).thenReturn(1L);
        lenient().when(wh.getUpdatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 6, 1, 12, 0));
        lenient().when(wh.getInvoicePeriodStart()).thenReturn(LocalDate.of(2026, 1, 1));
        lenient().when(wh.getInvoicePeriodEnd()).thenReturn(LocalDate.of(2026, 12, 31));
    }

    @Test
    void buildRows_WithEmptyEntities_ShouldReturnEmptyList() {
        List<String[]> rows = WarehouseExportPolicy.buildRows(List.of(), Map.of());
        assertThat(rows).isEmpty();
    }

    @Test
    void buildRows_WithSingleEntity_ShouldReturnOneRowWithCorrectFieldCount() {
        stubBasicFields();
        lenient().when(wh.getStatus()).thenReturn(WarehouseStatus.IN_USE);

        List<String[]> rows = WarehouseExportPolicy.buildRows(List.of(wh), Map.of(1L, List.of()));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).hasSize(WarehouseExportPolicy.HEADER_COUNT);
    }

    @Test
    void buildRows_ShouldPopulateBasicFieldsCorrectly() {
        stubBasicFields();

        List<String[]> rows = WarehouseExportPolicy.buildRows(List.of(wh), Map.of(1L, List.of()));
        String[] row = rows.get(0);

        assertThat(row[0]).isEqualTo("上海仓");
        assertThat(row[1]).isEqualTo("自营");
        assertThat(row[2]).isEqualTo("上海");
        assertThat(row[3]).isEqualTo("浦东新区XX路100号");
    }

    @Test
    void buildRows_WithBooleanFlags_ShouldUseChineseLabels() {
        stubBasicFields();

        List<String[]> rows = WarehouseExportPolicy.buildRows(List.of(wh), Map.of(1L, List.of()));
        String[] row = rows.get(0);

        assertThat(row[17]).isEqualTo("是");
        assertThat(row[19]).isEqualTo("否");
        assertThat(row[21]).isEqualTo("是");
    }

    @Test
    void buildRows_WithAttachments_ShouldResolveAttachmentNames() {
        stubBasicFields();
        lenient().when(certAttach.getType()).thenReturn(WarehouseAttachmentType.PROPERTY_CERTIFICATE);
        lenient().when(certAttach.getOriginalFilename()).thenReturn("产权证.pdf");
        lenient().when(invoiceAttach.getType()).thenReturn(WarehouseAttachmentType.INVOICE);
        lenient().when(invoiceAttach.getOriginalFilename()).thenReturn("发票.pdf");
        lenient().when(photoAttach.getType()).thenReturn(WarehouseAttachmentType.PHOTOS);
        lenient().when(photoAttach.getOriginalFilename()).thenReturn("照片1.jpg");

        List<String[]> rows = WarehouseExportPolicy.buildRows(
                List.of(wh),
                Map.of(1L, List.of(certAttach, invoiceAttach, photoAttach)));
        String[] row = rows.get(0);

        assertThat(row[18]).isEqualTo("产权证.pdf");
        assertThat(row[20]).isEqualTo("发票.pdf");
        assertThat(row[22]).isEqualTo("照片1.jpg");
        assertThat(row[28]).contains("产权证.pdf", "发票.pdf", "照片1.jpg");
    }

    @Test
    void buildRows_WithNullFields_ShouldReturnEmptyStrings() {
        lenient().when(wh.getId()).thenReturn(2L);
        lenient().when(wh.getName()).thenReturn(null);
        lenient().when(wh.getType()).thenReturn(null);
        lenient().when(wh.getProvince()).thenReturn(null);
        lenient().when(wh.getRegion()).thenReturn(null);
        lenient().when(wh.getAddress()).thenReturn(null);
        lenient().when(wh.getArea()).thenReturn(null);
        lenient().when(wh.getContactPerson()).thenReturn(null);
        lenient().when(wh.getHasPropertyCert()).thenReturn(null);
        lenient().when(wh.getHasInvoice()).thenReturn(null);
        lenient().when(wh.getHasPhotos()).thenReturn(null);
        lenient().when(wh.getEndDate()).thenReturn(null);
        lenient().when(wh.getStartDate()).thenReturn(null);
        lenient().when(wh.getClosePlan()).thenReturn(null);
        lenient().when(wh.getLessor()).thenReturn(null);
        lenient().when(wh.getLessee()).thenReturn(null);
        lenient().when(wh.getCertRemarks()).thenReturn(null);
        lenient().when(wh.getRemarks()).thenReturn(null);
        lenient().when(wh.getCreatedBy()).thenReturn(null);
        lenient().when(wh.getCreatedAt()).thenReturn(null);
        lenient().when(wh.getUpdatedBy()).thenReturn(null);
        lenient().when(wh.getUpdatedAt()).thenReturn(null);

        List<String[]> rows = WarehouseExportPolicy.buildRows(List.of(wh), Map.of(2L, List.of()));
        String[] row = rows.get(0);

        assertThat(row[0]).isEmpty();
        assertThat(row[1]).isEmpty();
        assertThat(row[17]).isEqualTo("否");
        assertThat(row[19]).isEqualTo("否");
        assertThat(row[21]).isEqualTo("否");
    }

    @Test
    void buildRows_WithMultipleEntities_ShouldReturnCorrectRowCount() {
        stubBasicFields();
        var wh2 = wh;
        lenient().when(wh2.getId()).thenReturn(2L);
        lenient().when(wh2.getName()).thenReturn("北京仓");

        List<String[]> rows = WarehouseExportPolicy.buildRows(
                List.of(wh, wh2),
                Map.of(1L, List.of(), 2L, List.of()));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)[0]).isEqualTo("北京仓");
    }

    @Test
    void buildRows_WithMissingWarehouseInAttachmentMap_ShouldNotThrow() {
        stubBasicFields();

        List<String[]> rows = WarehouseExportPolicy.buildRows(
                List.of(wh),
                Map.of());
        assertThat(rows).hasSize(1);
    }
}
