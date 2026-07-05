// Input: PlatformAccountRepository / PasswordEncryptionUtil / UserRepository mocks
// Output: PlatformAccountExportService unit tests — Excel export with header/data/password verification
// Pos: Test/服务层验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.UserRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAccountExportServiceTest {

    @Mock
    private PlatformAccountRepository accountRepository;
    @Mock
    private PasswordEncryptionUtil passwordEncryptionUtil;
    @Mock
    private UserRepository userRepository;

    private PlatformAccountExportService exportService;

    private static final String[] EXPECTED_HEADERS = {
            "平台名称", "账号", "密码", "网址", "账号保管员",
            "平台类型", "是否有CA", "注册人", "注册手机", "注册邮箱",
            "账号状态", "备注"
    };

    @BeforeEach
    void setUp() {
        exportService = new PlatformAccountExportService(
                accountRepository, passwordEncryptionUtil, userRepository);
    }

    @Test
    @DisplayName("导出空台账 — 仅表头行、Sheet 名正确")
    void exportToExcel_emptyData_returnsHeadersOnly() throws Exception {
        when(accountRepository.findAll()).thenReturn(List.of());

        byte[] result = exportService.exportToExcel(null);

        assertThat(result).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("平台账户台账");
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
        PlatformAccount account = PlatformAccount.builder()
                .id(1L)
                .accountName("政采云主账号")
                .username("admin001")
                .password("ENC_CIPHER_TEXT")
                .url("https://www.zcy.gov.cn")
                .contactPerson(10L)
                .platformType(PlatformType.GOV_PROCUREMENT)
                .hasCa(true)
                .registrant("张三")
                .registerPhone("13800138000")
                .registerEmail("zhangsan@example.com")
                .status(AccountStatus.AVAILABLE)
                .remarks("测试备注")
                .createdAt(LocalDateTime.now())
                .build();
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(passwordEncryptionUtil.decrypt("ENC_CIPHER_TEXT")).thenReturn("PlainP@ss1");
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());

        byte[] result = exportService.exportToExcel(null);

        assertThat(result).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            var dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("政采云主账号");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("admin001");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("PlainP@ss1");
            assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("https://www.zcy.gov.cn");
            assertThat(dataRow.getCell(5).getStringCellValue()).isEqualTo("政府采购网");
            assertThat(dataRow.getCell(6).getStringCellValue()).isEqualTo("是");
            assertThat(dataRow.getCell(7).getStringCellValue()).isEqualTo("张三");
            assertThat(dataRow.getCell(8).getStringCellValue()).isEqualTo("13800138000");
            assertThat(dataRow.getCell(9).getStringCellValue()).isEqualTo("zhangsan@example.com");
            assertThat(dataRow.getCell(10).getStringCellValue()).isEqualTo("可用");
            assertThat(dataRow.getCell(11).getStringCellValue()).isEqualTo("测试备注");
        }
    }

    @Test
    @DisplayName("按选中ID导出 — 只导出指定ID的记录")
    void exportToExcel_bySelectedIds_exportsOnlyMatching() throws Exception {
        PlatformAccount account1 = PlatformAccount.builder()
                .id(1L)
                .accountName("账号1")
                .username("user1")
                .password("ENC1")
                .platformType(PlatformType.BIDDING_PLATFORM)
                .hasCa(false)
                .status(AccountStatus.IN_USE)
                .createdAt(LocalDateTime.now())
                .build();
        PlatformAccount account2 = PlatformAccount.builder()
                .id(2L)
                .accountName("账号2")
                .username("user2")
                .password("ENC2")
                .platformType(PlatformType.OTHER)
                .hasCa(true)
                .status(AccountStatus.AVAILABLE)
                .createdAt(LocalDateTime.now())
                .build();
        when(accountRepository.findAllById(Set.of(1L))).thenReturn(List.of(account1));
        when(passwordEncryptionUtil.decrypt("ENC1")).thenReturn("pass1");

        byte[] result = exportService.exportToExcel(Set.of(1L));

        assertThat(result).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            var dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("账号1");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("user1");
        }
    }

    @Test
    @DisplayName("密码解密失败不中断导出 — 返回占位符")
    void exportToExcel_decryptFailure_returnsPlaceholder() throws Exception {
        PlatformAccount account = PlatformAccount.builder()
                .id(1L)
                .accountName("测试账号")
                .username("testuser")
                .password("BAD_CIPHER")
                .platformType(PlatformType.GOV_PROCUREMENT)
                .hasCa(false)
                .status(AccountStatus.AVAILABLE)
                .createdAt(LocalDateTime.now())
                .build();
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(passwordEncryptionUtil.decrypt("BAD_CIPHER")).thenThrow(new RuntimeException("decrypt error"));

        byte[] result = exportService.exportToExcel(null);

        assertThat(result).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            var dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("******");
        }
    }

    @Test
    @DisplayName("是否有CA输出是/否中文标签")
    void exportToExcel_hasCaColumn_returnsYesNoLabel() throws Exception {
        PlatformAccount withCa = PlatformAccount.builder()
                .id(1L).accountName("有CA").username("u1").password("p")
                .platformType(PlatformType.GOV_PROCUREMENT).hasCa(true)
                .status(AccountStatus.AVAILABLE).createdAt(LocalDateTime.now())
                .build();
        PlatformAccount withoutCa = PlatformAccount.builder()
                .id(2L).accountName("无CA").username("u2").password("p")
                .platformType(PlatformType.GOV_PROCUREMENT).hasCa(false)
                .status(AccountStatus.AVAILABLE).createdAt(LocalDateTime.now())
                .build();
        when(accountRepository.findAll()).thenReturn(List.of(withCa, withoutCa));
        when(passwordEncryptionUtil.decrypt(any())).thenReturn("decrypted");

        byte[] result = exportService.exportToExcel(null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("是");
            assertThat(sheet.getRow(2).getCell(6).getStringCellValue()).isEqualTo("否");
        }
    }
}
