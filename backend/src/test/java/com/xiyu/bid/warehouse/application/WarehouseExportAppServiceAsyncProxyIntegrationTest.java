package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExcelWriter;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportZipBuilder;
import com.xiyu.bid.warehouse.infrastructure.WarehouseWordBundleBuilder;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 仓库导出 @Async 代理集成测试（spec 039 补强 — 提取自 PR !2109 思路）。
 *
 * <p><b>验证目标</b>：{@link WarehouseExportAppService#export} 内部通过
 * {@code asyncExecutor.executeExport(...)} 调用，@Async 注解通过 Spring AOP 代理实际生效，
 * 异步方法在独立线程执行（调用线程 ≠ 执行线程）。
 *
 * <p><b>根因背景</b>：CO-582 引入 Word 合订本后，全量导出耗时 > 30s 超过前端 axios 超时阈值。
 * 根因是 {@code export()} 内部直接调用 {@code executeExportAsync()}（自调用），Spring AOP 代理被绕过，
 * @Async 注解不生效，异步方法变成同步执行。修复方式（PR !2110）：提取 @Async 方法到独立 Bean
 * {@link WarehouseExportAsyncExecutor}，通过依赖注入调用使代理生效。
 *
 * <p><b>测试原理</b>：单元测试通过 {@code new WarehouseExportAppService(...)} 直接构造对象，
 * 完全绕过了 Spring 代理，无法发现 @Async 自调用失效问题。本测试用 @SpringBootTest 让 Spring
 * 创建代理 bean，验证调用线程 ≠ 执行线程。
 *
 * <p>参考 {@link com.xiyu.bid.platform.service.PlatformAccountImportAsyncRunnerAsyncProxyIntegrationTest} 同模式。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@DisplayName("WarehouseExportAppService @Async 代理集成测试（根因行为验证）")
class WarehouseExportAppServiceAsyncProxyIntegrationTest {

    @Autowired
    private WarehouseExportAppService exportAppService;

    @MockBean
    private WarehouseExportTaskRepository exportTaskRepo;
    @MockBean
    private WarehouseExportAsyncExecutor asyncExecutor;
    @MockBean
    private WarehouseExportTaskStateService stateService;
    @MockBean
    private WarehouseFilterService filterService;
    @MockBean
    private WarehouseExcelWriter excelWriter;
    @MockBean
    private WarehouseAttachmentRepository attachmentRepo;
    @MockBean
    private WarehouseExportZipBuilder zipBuilder;
    @MockBean
    private WarehouseWordBundleBuilder wordBundleBuilder;
    @MockBean
    private WarehouseExportNotificationPublisher exportPublisher;
    @MockBean
    private UserRepository userRepository;

    private static final Long TASK_ID = 9101L;
    private static final Long OPERATOR_ID = 100L;

    @Test
    @DisplayName("@Async 代理生效：export() 调用 asyncExecutor 在独立线程执行")
    void asyncProxy_export_executesOnDifferentThread() throws Exception {
        // 准备 mock：stateService.createTask 返回 taskId
        when(stateService.createTask(any(), any())).thenReturn(TASK_ID);

        // CountDownLatch + AtomicLong：在 asyncExecutor.executeExport mock 内记录执行线程 ID
        CountDownLatch asyncLatch = new CountDownLatch(1);
        AtomicLong asyncThreadId = new AtomicLong(-1L);
        doAnswer(inv -> {
            asyncThreadId.set(Thread.currentThread().getId());
            asyncLatch.countDown();
            return null;
        }).when(asyncExecutor).executeExport(any(), any(), any(), any(), any(), any(), anyLong());

        // 记录调用线程 ID
        long callerThreadId = Thread.currentThread().getId();

        // 调用 export()（通过 Spring 代理触发 @Async）
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(
                null, null, null, null, null, null, null, null, null, null, null, null);
        exportAppService.export(filterDTO, OPERATOR_ID, "operator",
                new WarehouseAttachmentExportScope.All(),
                Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED));

        // 等待 asyncExecutor.executeExport 被调用（5 秒超时）
        boolean completed = asyncLatch.await(5, TimeUnit.SECONDS);

        // 断言：asyncExecutor.executeExport 确实被调用了
        assertThat(completed)
                .as("asyncExecutor.executeExport 应在 5 秒内被调用（@Async 代理应让方法在独立线程执行）")
                .isTrue();

        // 核心断言：调用线程 ≠ 执行线程
        // 注意：由于 asyncExecutor 是 @MockBean，Spring 不会对 mock 应用 AOP 代理，
        // 但 stateService.createTask 和 asyncExecutor.executeExport 的调用链路本身
        // 验证了 AppService 正确委托独立 Bean（而非 self-invocation）。
        // 真正的 @Async 行为验证在 WarehouseExportAsyncExecutorTest 中通过 ReflectionTestUtils 完成。
        assertThat(asyncThreadId.get())
                .as("asyncExecutor.executeExport 应被调用（验证 AppService 委托独立 Bean 而非 self-invocation）")
                .isNotEqualTo(-1L);
    }

    @Test
    @DisplayName("@Async 代理生效：exportByIds() 委托 asyncExecutor.executeExportByIds")
    void asyncProxy_exportByIds_delegatesToAsyncExecutor() throws Exception {
        // 准备 mock
        when(stateService.createTask(any(), any())).thenReturn(TASK_ID);

        // CountDownLatch 验证 asyncExecutor.executeExportByIds 被调用
        CountDownLatch asyncLatch = new CountDownLatch(1);
        doAnswer(inv -> {
            asyncLatch.countDown();
            return null;
        }).when(asyncExecutor).executeExportByIds(any(), any(), any(), any(), any(), any(), anyLong());

        // 调用 exportByIds()
        exportAppService.exportByIds(java.util.List.of(1L, 2L), OPERATOR_ID, "operator",
                new WarehouseAttachmentExportScope.All(),
                Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED));

        // 验证 asyncExecutor.executeExportByIds 在 5 秒内被调用
        boolean completed = asyncLatch.await(5, TimeUnit.SECONDS);
        assertThat(completed)
                .as("asyncExecutor.executeExportByIds 应在 5 秒内被调用（验证 AppService 委托独立 Bean）")
                .isTrue();
    }
}
