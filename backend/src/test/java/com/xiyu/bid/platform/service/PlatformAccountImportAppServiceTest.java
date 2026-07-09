package com.xiyu.bid.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import com.xiyu.bid.platform.infrastructure.persistence.repository.PlatformAccountImportTaskJpaRepository;
import com.xiyu.bid.repository.UserRepository;
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
 * PlatformAccountImportAppService 测试（CO-560 重构后）。
 *
 * <p>重构后 AppService 仅负责同步阶段（创建 PENDING 任务 + 委托给 AsyncRunner）。
 * 异步执行逻辑的测试迁移到 {@link PlatformAccountImportAsyncRunnerTest}。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformAccountImportAppService 平台账户导入应用服务（同步阶段）")
class PlatformAccountImportAppServiceTest {

    @Mock
    private PlatformAccountImportAsyncRunner asyncRunner;
    @Mock
    private PlatformAccountImportTaskJpaRepository taskRepo;
    @Mock
    private UserRepository userRepository;

    private PlatformAccountImportAppService service;

    private static final Long TASK_ID = 1L;
    private static final Long OPERATOR_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new PlatformAccountImportAppService(asyncRunner, taskRepo, userRepository);

        // save 时模拟 DB 自动分配 ID
        lenient().when(taskRepo.save(any())).thenAnswer(inv -> {
            PlatformAccountImportTaskEntity t = inv.getArgument(0);
            if (t.getId() == null) t.setId(TASK_ID);
            return t;
        });
    }

    @Nested
    @DisplayName("triggerImport — 同步阶段")
    class TriggerImport {

        @Test
        @DisplayName("创建 PENDING 任务并委托给 AsyncRunner")
        void createsPendingTask_andDelegatesToAsyncRunner() {
            byte[] fileBytes = new byte[]{1, 2, 3};
            String filename = "test.xlsx";

            Long taskId = service.triggerImport(fileBytes, filename, OPERATOR_ID);

            assertThat(taskId).isEqualTo(TASK_ID);

            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo).save(taskCaptor.capture());
            PlatformAccountImportTaskEntity savedTask = taskCaptor.getValue();
            assertThat(savedTask.getStatus()).isEqualTo("PENDING");
            assertThat(savedTask.getSourceFilename()).isEqualTo(filename);
            assertThat(savedTask.getCreatedBy()).isEqualTo(OPERATOR_ID);

            verify(asyncRunner).executeImportAsync(eq(TASK_ID), eq(fileBytes), eq(OPERATOR_ID));
        }

        @Test
        @DisplayName("操作人 username 正确回填到 createdByUsername")
        void fillsCreatedByUsername_fromUser() {
            User operator = User.builder().id(OPERATOR_ID).fullName("张三").build();
            lenient().when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator));

            service.triggerImport(new byte[0], "test.xlsx", OPERATOR_ID);

            ArgumentCaptor<PlatformAccountImportTaskEntity> taskCaptor =
                    ArgumentCaptor.forClass(PlatformAccountImportTaskEntity.class);
            verify(taskRepo).save(taskCaptor.capture());
            assertThat(taskCaptor.getValue().getCreatedByUsername()).isEqualTo("张三");
        }
    }
}
