// Input: CaCertificateRepository / CaCertificatePlatformRepository / PasswordEncryptionUtil mocks
// Output: CaCertificateExportService unit tests — Excel export with header/data/password verification
// Pos: Test/纯核心验证
package com.xiyu.bid.resources.service;

import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificatePlatformEntity;
import com.xiyu.bid.resources.repository.CaCertificatePlatformRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaCertificateExportServiceTest {

    @Mock
    private CaCertificateRepository certificateRepository;
    @Mock
    private CaCertificatePlatformRepository platformLinkRepository;
    @Mock
    private PasswordEncryptionUtil passwordEncryptionUtil;

    private CaCertificateExportService exportService;

    private static final String[] EXPECTED_HEADERS = {
            "CA类型", "印章类型", "持有人", "保管员姓名",
            "有效期至", "颁发机构", "电子账号", "CA密码",
            "平台URL", "关联平台ID", "借用状态", "证书状态", "备注"
    };

    @BeforeEach
    void setUp() {
        exportService = new CaCertificateExportService(
                certificateRepository, platformLinkRepository, passwordEncryptionUtil);
    }

    @Test
    @DisplayName("导出空台账 — 仅表头行、Sheet 名正确")
    void exportToExcel_emptyData_returnsHeadersOnly() throws Exception {
        when(certificateRepository.findAll(any(Specification.class))).thenReturn(List.of());

        byte[] result = exportService.exportToExcel(
                new CaCertificateExportService.CaExportFilters(null, null, null, null, null), null);

        assertThat(result).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("CA证书台账");
            assertThat(sheet.getLastRowNum()).isEqualTo(0);
            var headerRow = sheet.getRow(0);
            assertThat(headerRow.getPhysicalNumberOfCells()).isEqualTo(EXPECTED_HEADERS.length);
            for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
                assertThat(headerRow.getCell(i).getStringCellValue()).isEqualTo(EXPECTED_HEADERS[i]);
            }
        }
    }

    @Test
    @DisplayName("按筛选导出 — 数据行写入正确，状态值输出中文标签")
    void exportToExcel_byFilters_writesRowsWithChineseLabels() throws Exception {
        CaCertificateEntity entity = CaCertificateEntity.builder()
                .id(1L)
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .holderName("张三")
                .custodianId(10L)
                .custodianName("李保管")
                .expiryDate(LocalDate.of(2026, 12, 31))
                .issuer("某CA机构")
                .electronicAccount("acc@test.com")
                .caPassword("ENC_CIPHER_TEXT")
                .caPlatformUrl("https://ca.example.com")
                .borrowStatus("IN_STOCK")
                .status("ACTIVE")
                .remarks("测试备注")
                .build();
        when(certificateRepository.findAll(any(Specification.class))).thenReturn(List.of(entity));
        when(platformLinkRepository.findByCaCertificateIdIn(anyCollection())).thenReturn(List.of());
        when(passwordEncryptionUtil.decrypt("ENC_CIPHER_TEXT")).thenReturn("PlainP@ss1");

        byte[] result = exportService.exportToExcel(
                new CaCertificateExportService.CaExportFilters("ACTIVE", null, null, null, null), null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("实体CA");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("公章");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("张三");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("李保管");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("2026-12-31");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("某CA机构");
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("acc@test.com");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("PlainP@ss1");
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("https://ca.example.com");
            assertThat(row.getCell(9).getStringCellValue()).isEmpty();
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("在库");
            assertThat(row.getCell(11).getStringCellValue()).isEqualTo("有效");
            assertThat(row.getCell(12).getStringCellValue()).isEqualTo("测试备注");
        }
    }

    @Test
    @DisplayName("按选中 ID 导出 — 调用 findAllById，忽略筛选条件")
    void exportToExcel_bySelectedIds_usesFindAllById() throws Exception {
        CaCertificateEntity entity = CaCertificateEntity.builder()
                .id(5L)
                .caType("ELECTRONIC_CA")
                .sealType("LEGAL_SIGN")
                .holderName("王五")
                .custodianId(20L)
                .custodianName("赵保管")
                .expiryDate(LocalDate.of(2027, 6, 1))
                .borrowStatus("BORROWED")
                .status("EXPIRING")
                .build();
        when(certificateRepository.findAllById(anyCollection())).thenReturn(List.of(entity));
        when(platformLinkRepository.findByCaCertificateIdIn(anyCollection())).thenReturn(List.of());

        // 即使传了 filters，因为有 selectedIds，应走 findAllById 路径
        byte[] result = exportService.exportToExcel(
                new CaCertificateExportService.CaExportFilters("ACTIVE", null, null, null, null),
                Set.of(5L));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("电子CA");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("法人签字");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("已借出");
            assertThat(row.getCell(11).getStringCellValue()).isEqualTo("即将到期");
        }
    }

    @Test
    @DisplayName("关联平台 ID — 多个 ID 以逗号拼接输出")
    void exportToExcel_multiplePlatformIds_joinedByComma() throws Exception {
        CaCertificateEntity entity = CaCertificateEntity.builder()
                .id(1L)
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .holderName("张三")
                .custodianId(10L)
                .custodianName("李保管")
                .expiryDate(LocalDate.of(2026, 12, 31))
                .build();
        when(certificateRepository.findAll(any(Specification.class))).thenReturn(List.of(entity));
        when(platformLinkRepository.findByCaCertificateIdIn(anyCollection())).thenReturn(List.of(
                platformLink(1L, 100L),
                platformLink(1L, 200L),
                platformLink(1L, 300L)
        ));

        byte[] result = exportService.exportToExcel(
                new CaCertificateExportService.CaExportFilters(null, null, null, null, null), null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var row = wb.getSheetAt(0).getRow(1);
            assertThat(row.getCell(9).getStringCellValue()).isEqualTo("100,200,300");
        }
    }

    @Test
    @DisplayName("密码为空 — 输出空字符串，不调用解密")
    void exportToExcel_emptyPassword_outputsEmptyString() throws Exception {
        CaCertificateEntity entity = CaCertificateEntity.builder()
                .id(1L)
                .caType("ELECTRONIC_CA")
                .sealType("OFFICIAL_SEAL")
                .holderName("张三")
                .custodianId(10L)
                .custodianName("李保管")
                .expiryDate(LocalDate.of(2026, 12, 31))
                .caPassword(null)
                .build();
        when(certificateRepository.findAll(any(Specification.class))).thenReturn(List.of(entity));
        when(platformLinkRepository.findByCaCertificateIdIn(anyCollection())).thenReturn(List.of());

        byte[] result = exportService.exportToExcel(
                new CaCertificateExportService.CaExportFilters(null, null, null, null, null), null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var row = wb.getSheetAt(0).getRow(1);
            assertThat(row.getCell(7).getStringCellValue()).isEmpty();
        }
    }

    private CaCertificatePlatformEntity platformLink(Long caId, Long platformId) {
        return CaCertificatePlatformEntity.builder()
                .caCertificateId(caId)
                .platformAccountId(platformId)
                .build();
    }
}
