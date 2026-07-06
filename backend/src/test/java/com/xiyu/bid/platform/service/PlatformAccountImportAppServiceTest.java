package com.xiyu.bid.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader.WorkbookData;
import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import com.xiyu.bid.platform.infrastructure.persistence.repository.PlatformAccountImportTaskJpaRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformAccountImportAppService 平台账户导入应用服务")
class PlatformAccountImportAppServiceTest {

    @Mock
    private SingleSheetExcelReader excelReader;
    @Mock
    private PlatformAccountImportRowPersister rowPersister;
    @Mock
    private PlatformAccountImportTaskJpaRepository taskRepo;
    @Mock
    private PlatformAccountRepository accountRepo;
    @Mock
    private UserRepository userRepository;

    private PlatformAccountImportAppService service;

    private static final Long TASK_ID = 1L;
    private static final Long OPERATOR_ID = 100L;
    private static final String VALID_EMPLOYEE_NUMBER = "EMP001";
    private static final Long CUSTODIAN_USER_ID = 42L;

    @BeforeEach
    void setUp() {
        service = new PlatformAccountImportAppService(
                excelReader, rowPersister, taskRepo, accountRepo, userRepository);

        PlatformAccountImportTaskEntity task = new PlatformAccountImportTaskEntity();
        task.setId(TASK_ID);
        lenient().when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
        lenient().when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(accountRepo.findByAccountName(anyString())).thenReturn(Optional.empty());
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

            service.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

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

            service.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

            verify(rowPersister, never()).persist(any(), any());

            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo, org.mockito.Mockito.atLeastOnce()).save(taskCaptor.capture());
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

            service.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

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
        @DisplayName("平台名称重复：第二行应提示重复")
        void duplicateAccountName_secondRowRejected() throws Exception {
            User custodian = User.builder().id(CUSTODIAN_USER_ID).employeeNumber(VALID_EMPLOYEE_NUMBER).build();
            when(excelReader.read(any(byte[].class)))
                    .thenReturn(buildWorkbookWithRows(
                            row("同一平台", "user1", VALID_EMPLOYEE_NUMBER),
                            row("同一平台", "user2", VALID_EMPLOYEE_NUMBER)));
            when(userRepository.findByEmployeeNumber(VALID_EMPLOYEE_NUMBER)).thenReturn(Optional.of(custodian));
            when(accountRepo.findByAccountName("同一平台"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(new PlatformAccount()));

            service.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

            verify(rowPersister, times(1)).persist(any(), any());
            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo, atLeastOnce()).save(taskCaptor.capture());
            PlatformAccountImportTaskEntity finalTask = taskCaptor.getAllValues().stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .findFirst()
                    .orElseThrow();
            assertThat(finalTask.getImportedRows()).isEqualTo(1);
            assertThat(finalTask.getErrorDetails()).contains("平台名称");
            assertThat(finalTask.getErrorDetails()).contains("已存在");
        }
    }
}
