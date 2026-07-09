package com.xiyu.bid.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader.WorkbookData;
import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy;
import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import com.xiyu.bid.platform.infrastructure.persistence.repository.PlatformAccountImportTaskJpaRepository;
import com.xiyu.bid.repository.UserRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PlatformAccountImportAsyncRunner 测试（CO-560）。
 *
 * <p>验证异步执行逻辑（原 PlatformAccountImportAppServiceTest 的 executeImportAsync 测试迁移至此）。
 * 测试范围：
 * <ul>
 *   <li>工号解析（有效/无效）</li>
 *   <li>已取消登录账号唯一性校验</li>
 *   <li>字段长度校验（CO-560 P0-1：超长字段行级失败不触发 persist）</li>
 *   <li>单行 persist 异常隔离（CO-560 P1-1：REQUIRES_NEW 传播下单行失败不中断后续行）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformAccountImportAsyncRunner 平台账户导入异步执行器")
class PlatformAccountImportAsyncRunnerTest {

    @Mock
    private SingleSheetExcelReader excelReader;
    @Mock
    private PlatformAccountImportRowPersister rowPersister;
    @Mock
    private PlatformAccountImportTaskJpaRepository taskRepo;
    @Mock
    private UserRepository userRepository;

    private PlatformAccountImportAsyncRunner runner;

    private static final Long TASK_ID = 1L;
    private static final Long OPERATOR_ID = 100L;
    private static final String VALID_EMPLOYEE_NUMBER = "EMP001";
    private static final Long CUSTODIAN_USER_ID = 42L;

    @BeforeEach
    void setUp() {
        runner = new PlatformAccountImportAsyncRunner(
                excelReader, rowPersister, taskRepo, userRepository);

        PlatformAccountImportTaskEntity task = new PlatformAccountImportTaskEntity();
        task.setId(TASK_ID);
        lenient().when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
        // save 时模拟 DB 自动分配 ID
        lenient().when(taskRepo.save(any())).thenAnswer(inv -> {
            PlatformAccountImportTaskEntity t = inv.getArgument(0);
            if (t.getId() == null) t.setId(TASK_ID);
            return t;
        });
    }

    private WorkbookData buildWorkbook(String employeeNumber) {
        String[] header = PlatformAccountImportPolicy.HEADERS.clone();
        String[] row = {
                "测试平台", "https://example.com", "admin", "pass123",
                employeeNumber, "否", "",
                "", "", ""
        };
        return new WorkbookData(List.of(header, row));
    }

    @Nested
    @DisplayName("executeImportAsync — 工号解析")
    class EmployeeNumberResolution {

        @Test
        @DisplayName("有效工号：成功解析为 userId，persist 收到正确的 contactPersonId")
        void validEmployeeNumber_resolvesToUserId() throws Exception {
            User custodian = User.builder().id(CUSTODIAN_USER_ID).employeeNumber(VALID_EMPLOYEE_NUMBER).build();
            when(excelReader.read(any(byte[].class))).thenReturn(buildWorkbook(VALID_EMPLOYEE_NUMBER));
            when(userRepository.findByEmployeeNumber(VALID_EMPLOYEE_NUMBER)).thenReturn(Optional.of(custodian));

            runner.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

            ArgumentCaptor<Long> contactPersonCaptor = ArgumentCaptor.forClass(Long.class);
            verify(rowPersister).persist(any(), contactPersonCaptor.capture());
            assertThat(contactPersonCaptor.getValue()).isEqualTo(CUSTODIAN_USER_ID);
        }

        @Test
        @DisplayName("无效工号：不调用 persist，任务错误包含提示")
        void invalidEmployeeNumber_addsErrorAndSkipsPersist() throws Exception {
            String badNumber = "BAD999";
            when(excelReader.read(any(byte[].class))).thenReturn(buildWorkbook(badNumber));
            when(userRepository.findByEmployeeNumber(badNumber)).thenReturn(Optional.empty());

            runner.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

            verify(rowPersister, never()).persist(any(), any());

            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo, atLeastOnce()).save(taskCaptor.capture());
            PlatformAccountImportTaskEntity finalTask = taskCaptor.getAllValues().stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .findFirst()
                    .orElseThrow();
            assertThat(finalTask.getErrorDetails()).contains("工号「" + badNumber + "」未匹配到用户");
            assertThat(finalTask.getImportedRows()).isEqualTo(0);
            assertThat(finalTask.getInvalidRows()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("executeImportAsync — 已取消登录账号唯一性校验")
    class UsernameUniquenessRemoved {

        private WorkbookData buildWorkbookWithRows(String[]... rows) {
            String[] header = PlatformAccountImportPolicy.HEADERS.clone();
            List<String[]> data = new ArrayList<>();
            data.add(header);
            data.addAll(Arrays.asList(rows));
            return new WorkbookData(data);
        }

        private String[] row(String accountName, String username, String employeeNumber) {
            return new String[]{
                    accountName, "https://example.com", username, "pass123",
                    employeeNumber, "否", "",
                    "", "", ""
            };
        }

        @Test
        @DisplayName("相同登录账号重复出现：所有行都应导入成功")
        void duplicateUsername_rowsAllowed() throws Exception {
            User custodian = User.builder().id(CUSTODIAN_USER_ID).employeeNumber(VALID_EMPLOYEE_NUMBER).build();
            when(excelReader.read(any(byte[].class)))
                    .thenReturn(buildWorkbookWithRows(
                            row("平台A", "admin", VALID_EMPLOYEE_NUMBER),
                            row("平台B", "admin", VALID_EMPLOYEE_NUMBER)));
            when(userRepository.findByEmployeeNumber(VALID_EMPLOYEE_NUMBER)).thenReturn(Optional.of(custodian));

            runner.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

            verify(rowPersister, times(2)).persist(any(), any());
            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo, atLeastOnce()).save(taskCaptor.capture());
            PlatformAccountImportTaskEntity finalTask = taskCaptor.getAllValues().stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .findFirst()
                    .orElseThrow();
            assertThat(finalTask.getImportedRows()).isEqualTo(2);
            assertThat(finalTask.getInvalidRows()).isEqualTo(0);
        }

        @Test
        @DisplayName("CO-559: 平台名称重复允许导入（移除唯一性校验）")
        void duplicateAccountName_bothRowsImported() throws Exception {
            User custodian = User.builder().id(CUSTODIAN_USER_ID).employeeNumber(VALID_EMPLOYEE_NUMBER).build();
            when(excelReader.read(any(byte[].class)))
                    .thenReturn(buildWorkbookWithRows(
                            row("同一平台", "user1", VALID_EMPLOYEE_NUMBER),
                            row("同一平台", "user2", VALID_EMPLOYEE_NUMBER)));
            when(userRepository.findByEmployeeNumber(VALID_EMPLOYEE_NUMBER)).thenReturn(Optional.of(custodian));

            runner.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

            verify(rowPersister, times(2)).persist(any(), any());
            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo, atLeastOnce()).save(taskCaptor.capture());
            PlatformAccountImportTaskEntity finalTask = taskCaptor.getAllValues().stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .findFirst()
                    .orElseThrow();
            assertThat(finalTask.getImportedRows()).isEqualTo(2);
            assertThat(finalTask.getInvalidRows()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("executeImportAsync — 字段长度校验（CO-560 P0-1）")
    class FieldLengthValidationInImport {

        @Test
        @DisplayName("注册手机号超长：行级失败，不调用 persist")
        void registerPhoneTooLong_rowFailsWithoutPersist() throws Exception {
            String[] header = PlatformAccountImportPolicy.HEADERS.clone();
            String[] row = {
                    "测试平台", "https://example.com", "admin", "pass123",
                    VALID_EMPLOYEE_NUMBER, "否", "",
                    "", "13800138000138001380001", "" // 23 字符，超过 LEN_PHONE=20
            };
            when(excelReader.read(any(byte[].class))).thenReturn(new WorkbookData(List.of(header, row)));
            // stub userRepository 以避免 UnnecessaryStubbing（即使 row.valid()=false 不进入 persist）
            lenient().when(userRepository.findByEmployeeNumber(VALID_EMPLOYEE_NUMBER))
                    .thenReturn(Optional.empty());

            runner.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

            // 行级失败：不调用 persist
            verify(rowPersister, never()).persist(any(), any());

            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo, atLeastOnce()).save(taskCaptor.capture());
            PlatformAccountImportTaskEntity finalTask = taskCaptor.getAllValues().stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .findFirst()
                    .orElseThrow();
            assertThat(finalTask.getImportedRows()).isEqualTo(0);
            assertThat(finalTask.getInvalidRows()).isEqualTo(1);
            assertThat(finalTask.getErrorDetails()).contains("注册手机");
        }
    }

    @Nested
    @DisplayName("executeImportAsync — 单行 persist 异常隔离（CO-560 P1-1）")
    class RowPersistFailureIsolation {

        @Test
        @DisplayName("第一行 persist 抛异常：后续行仍正常导入（REQUIRES_NEW 传播）")
        void firstRowFails_subsequentRowsStillImport() throws Exception {
            User custodian = User.builder().id(CUSTODIAN_USER_ID).employeeNumber(VALID_EMPLOYEE_NUMBER).build();
            String[] header = PlatformAccountImportPolicy.HEADERS.clone();
            String[] row1 = {"平台A", "https://a.com", "user1", "pass1",
                    VALID_EMPLOYEE_NUMBER, "否", "", "", "", ""};
            String[] row2 = {"平台B", "https://b.com", "user2", "pass2",
                    VALID_EMPLOYEE_NUMBER, "否", "", "", "", ""};
            when(excelReader.read(any(byte[].class)))
                    .thenReturn(new WorkbookData(List.of(header, row1, row2)));
            when(userRepository.findByEmployeeNumber(VALID_EMPLOYEE_NUMBER)).thenReturn(Optional.of(custodian));

            // 第一行 persist 抛异常，第二行正常
            doThrow(new RuntimeException("模拟 DB 异常"))
                    .doNothing()
                    .when(rowPersister).persist(any(), any());

            runner.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

            // 两行都被尝试 persist
            verify(rowPersister, times(2)).persist(any(), any());

            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo, atLeastOnce()).save(taskCaptor.capture());
            PlatformAccountImportTaskEntity finalTask = taskCaptor.getAllValues().stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .findFirst()
                    .orElseThrow();
            // 第一行失败，第二行成功
            assertThat(finalTask.getImportedRows()).isEqualTo(1);
            assertThat(finalTask.getErrorDetails()).contains("模拟 DB 异常");
        }
    }
}
