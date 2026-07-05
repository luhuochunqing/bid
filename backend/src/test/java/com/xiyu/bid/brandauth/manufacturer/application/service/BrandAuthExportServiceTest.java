package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BrandAuthExportService 单元测试。
 *
 * <p>核心覆盖点：exportByFilter 按 authorizationType 决定生成 sheet 数量。
 * 修复前总是生成两个 sheet（原厂+代理商），修复后按当前 tab 类型只生成对应 sheet。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrandAuthExportServiceTest {

    @Mock
    private ListManufacturerAuthAppService listService;
    @Mock
    private BrandAuthAttachmentJpaRepository attachmentRepository;

    @InjectMocks
    private BrandAuthExportService exportService;

    @Test
    @DisplayName("authorizationType=MANUFACTURER 时只生成原厂授权sheet")
    void exportByFilter_manufacturerType_onlyManufacturerSheet() throws IOException {
        var mfgAuth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌A", "国产", "原厂A",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L);
        when(listService.listAllForExport(any())).thenReturn(List.of(mfgAuth));
        when(attachmentRepository.findByAuthorizationIdIn(any())).thenReturn(List.of());

        var filter = new ListManufacturerAuthAppService.ListFilter(
                null, null, null, null, null,
                null, null, null, null,
                null, null, "MANUFACTURER");

        byte[] data = exportService.exportByFilter(filter);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            assertEquals(1, wb.getNumberOfSheets(),
                    "MANUFACTURER 类型应只生成1个sheet");
            assertEquals("原厂授权", wb.getSheetName(0));
        }
    }

    @Test
    @DisplayName("authorizationType=AGENT 时只生成代理商授权sheet")
    void exportByFilter_agentType_onlyAgentSheet() throws IOException {
        var agentAuth = ManufacturerAuthorization.createAgent(
                ProductLine.TOOLS, "BR-002", "品牌B", "进口", "原厂B", "代理商B",
                LocalDate.now(), LocalDate.now().plusDays(180),
                LocalDate.now(), LocalDate.now().plusDays(180), "remarks1",
                LocalDate.now(), LocalDate.now().plusDays(180), "remarks2",
                null, 1L);
        when(listService.listAllForExport(any())).thenReturn(List.of(agentAuth));
        when(attachmentRepository.findByAuthorizationIdIn(any())).thenReturn(List.of());

        var filter = new ListManufacturerAuthAppService.ListFilter(
                null, null, null, null, null,
                null, null, null, null,
                null, null, "AGENT");

        byte[] data = exportService.exportByFilter(filter);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            assertEquals(1, wb.getNumberOfSheets(),
                    "AGENT 类型应只生成1个sheet");
            assertEquals("代理商授权", wb.getSheetName(0));
        }
    }

    @Test
    @DisplayName("authorizationType=null 时生成两个sheet（向后兼容）")
    void exportByFilter_nullType_bothSheets() throws IOException {
        var mfgAuth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌A", "国产", "原厂A",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L);
        var agentAuth = ManufacturerAuthorization.createAgent(
                ProductLine.TOOLS, "BR-002", "品牌B", "进口", "原厂B", "代理商B",
                LocalDate.now(), LocalDate.now().plusDays(180),
                LocalDate.now(), LocalDate.now().plusDays(180), "remarks1",
                LocalDate.now(), LocalDate.now().plusDays(180), "remarks2",
                null, 1L);
        when(listService.listAllForExport(any())).thenReturn(List.of(mfgAuth, agentAuth));
        when(attachmentRepository.findByAuthorizationIdIn(any())).thenReturn(List.of());

        var filter = new ListManufacturerAuthAppService.ListFilter(
                null, null, null, null, null,
                null, null, null, null,
                null, null, null);

        byte[] data = exportService.exportByFilter(filter);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            assertEquals(2, wb.getNumberOfSheets(),
                    "null 类型应生成2个sheet（兼容）");
            assertEquals("原厂授权", wb.getSheetName(0));
            assertEquals("代理商授权", wb.getSheetName(1));
        }
    }
}
