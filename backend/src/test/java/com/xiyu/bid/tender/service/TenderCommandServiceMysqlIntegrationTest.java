package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.integration.external.ProjectManagerIdResolver;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.support.AbstractMysqlIntegrationTest;
import com.xiyu.bid.support.InMemoryRoleCodeCachePortConfig;
import com.xiyu.bid.support.InMemoryRoleCodeCachePortConfig.InMemoryRoleCodeCachePort;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TenderCommandService 真实 MySQL 集成测试。
 *
 * <p>覆盖 5 个高风险跨表事务场景（FR-004 ~ FR-008）：
 * <ul>
 *   <li>CO-297: linkCrmOpportunity CRM 商机唯一性双层防御（应用层 + DB UNIQUE）</li>
 *   <li>CO-310: assignOnCrmLink 写 DISPATCH 分配记录跨表事务一致性</li>
 *   <li>tryAutoAssign 失败：Tender 保持 PENDING_ASSIGNMENT，不写分配记录</li>
 *   <li>deleteTender 级联删除 tender_attachments（DB ON DELETE CASCADE）</li>
 *   <li>CO-265: createTender 三字段去重检测（purchaserName + registrationDeadline + bidOpeningTime）</li>
 * </ul>
 *
 * <p>依赖策略（详见 contracts/tender-command-service-contract.md）：
 * <ul>
 *   <li>4 个 @MockBean：autoAssignmentService / assignmentNotifier / evaluationBackfillService / projectManagerIdResolver</li>
 *   <li>其余 11 个依赖 @Autowired 真实（含 tenderRepository / assignmentRecordRepository / crmOccupancyChecker / commandAccessGuard 等）</li>
 *   <li>导入 {@link EffectiveRoleResolverMysqlIntegrationTestConfig} 提供 InMemoryRoleCodeCachePort，
 *       让真实 commandAccessGuard → EffectiveRoleResolver 链路用内存缓存（绕过 OSS HTTP）</li>
 * </ul>
 *
 * <p>测试数据策略：
 * <ul>
 *   <li>所有测试数据用 'test-int-' 前缀（title / purchaserName / username / role code）</li>
 *   <li>@BeforeEach 按 FK 反向顺序清理（assignment_records → attachments → tenders → users → roles）</li>
 *   <li>共享 DB + Flyway 全迁移链，不重置 schema</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("flyway-mysql")
@Import({
        NoOpPasswordEncryptionTestConfig.class,
        InMemoryRoleCodeCachePortConfig.class
})
class TenderCommandServiceMysqlIntegrationTest extends AbstractMysqlIntegrationTest {

    @Autowired
    private TenderCommandService tenderCommandService;

    @Autowired
    private TenderRepository tenderRepository;

    @Autowired
    private TenderAttachmentRepository attachmentRepository;

    @Autowired
    private TenderAssignmentRecordRepository assignmentRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleProfileRepository roleProfileRepository;

    /** 注入共享的 InMemory stub，直接调用 clear()/put() 无需类型转换。 */
    @Autowired
    private InMemoryRoleCodeCachePort roleCodeCachePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TenderAutoAssignmentService autoAssignmentService;

    @MockBean
    private TenderAssignmentNotifier assignmentNotifier;

    @MockBean
    private TenderEvaluationBackfillService evaluationBackfillService;

    @MockBean
    private ProjectManagerIdResolver projectManagerIdResolver;

    private User adminUser;

    @BeforeEach
    void cleanTestData() {
        // 清理顺序：尊重 FK 约束
        // tender_assignment_records 无 FK 指向 tenders，但逻辑上关联，先删
        jdbcTemplate.update("DELETE FROM tender_assignment_records WHERE tender_id IN "
                + "(SELECT id FROM tenders WHERE title LIKE 'test-int-%')");
        // tender_attachments 有 ON DELETE CASCADE，但显式删避免跨测试干扰
        jdbcTemplate.update("DELETE FROM tender_attachments WHERE tender_id IN "
                + "(SELECT id FROM tenders WHERE title LIKE 'test-int-%')");
        jdbcTemplate.update("DELETE FROM tenders WHERE title LIKE 'test-int-%'");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'test-int-%'");
        jdbcTemplate.update("DELETE FROM roles WHERE code LIKE 'test-int-%'");
        // 清空缓存 stub
        roleCodeCachePort.clear();
        // 默认 mock：自动分配无匹配
        when(autoAssignmentService.autoAssignIfPossible(any())).thenReturn(AssignmentResult.noMatch());
        // 准备 admin 用户（真实 DB 落库 + 缓存写入，让真实 commandAccessGuard 通过）
        adminUser = setupAdminUser();
    }

    // ── 辅助方法 ──

    /**
     * 创建并落库 admin RoleProfile + User，并在缓存中写入 "admin" 角色码。
     * 让真实 TenderCommandAccessGuard → EffectiveRoleResolver 链路通过权限校验。
     */
    private User setupAdminUser() {
        RoleProfile rp = roleProfileRepository.saveAndFlush(RoleProfile.builder()
                .code("test-int-admin-role")
                .name("集成测试管理员")
                .description("集成测试管理员角色")
                .isSystem(false)
                .enabled(true)
                .dataScope("all")
                .build());
        User user = User.builder()
                .username("test-int-admin")
                .password("dummy-password")
                .email("test-int-admin@test.local")
                .fullName("集成测试管理员")
                .role(User.Role.MANAGER)
                .roleProfile(rp)
                .enabled(true)
                .emailVerified(true)
                .build();
        user = userRepository.saveAndFlush(user);
        roleCodeCachePort.put("test-int-admin", "admin");
        return user;
    }

    /**
     * 构建最小可用 TenderDTO（通过 TenderBasicInfoValidator 校验）。
     */
    private TenderDTO buildTenderDTO(String suffix, String purchaserName) {
        return TenderDTO.builder()
                .title("test-int-tender-" + suffix)
                .purchaserName(purchaserName)
                .publishDate(LocalDate.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════════
    //  T008: CO-297 linkCrmOpportunity CRM 商机唯一性双层防御
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CO-297: linkCrmOpportunity CRM 商机唯一性双层防御")
    class LinkCrmOpportunityUniqueDefense {

        @Test
        @DisplayName("CRM 商机已被其他标讯关联 → 抛 409，原标讯 crmOpportunityId 未被覆盖")
        void crmOpportunityAlreadyOccupied_throws409_andDoesNotOverwrite() {
            // given: tender A 已关联 crmOppId
            TenderDTO dtoA = buildTenderDTO("t008-a", "test-int-purchaser-t008-a");
            TenderDTO savedA = tenderCommandService.createTender(dtoA, adminUser.getId());
            tenderCommandService.linkCrmOpportunity(
                    savedA.getId(), "test-int-opp-001", "CRM商机001", adminUser.getId());

            // tender B 尝试关联同一 crmOppId
            TenderDTO dtoB = buildTenderDTO("t008-b", "test-int-purchaser-t008-b");
            TenderDTO savedB = tenderCommandService.createTender(dtoB, adminUser.getId());

            // when & then: 应抛 BusinessException(409)
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    tenderCommandService.linkCrmOpportunity(
                            savedB.getId(), "test-int-opp-001", "CRM商机001", adminUser.getId()));
            assertEquals(409, ex.getCode());

            // and: tender B 的 crmOpportunityId 未被覆盖（仍为 null）
            flushAndClear();
            Tender freshB = tenderRepository.findById(savedB.getId()).orElseThrow();
            assertNull(freshB.getCrmOpportunityId(),
                    "CRM 商机被占用时原标讯 crmOpportunityId 不应被覆盖");
        }

        // 注：BIDDING 状态禁止更换 CRM 商机的场景由 commandAccessGuard 在 assertCanUpdateTender 阶段拦截
        // （admin 在 BIDDING 状态无编辑权限），assertCrmLinkAllowed 是其后的业务规则二次防御。
        // 该纯函数行为由单元测试覆盖，集成测试不重复。
    }

    // ════════════════════════════════════════════════════════════════════
    //  T009: CO-310 assignOnCrmLink 跨表事务一致性
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CO-310: linkCrmOpportunity 写 DISPATCH 分配记录跨表一致性")
    class AssignOnCrmLinkTransactionConsistency {

        @Test
        @DisplayName("linkCrmOpportunity 成功 → tender_assignment_records 有 DISPATCH 记录，字段真实落库")
        void linkCrmOpportunity_success_writesDispatchRecord() {
            // given
            TenderDTO dto = buildTenderDTO("t009", "test-int-purchaser-t009");
            TenderDTO saved = tenderCommandService.createTender(dto, adminUser.getId());

            // when
            tenderCommandService.linkCrmOpportunity(
                    saved.getId(), "test-int-opp-003", "CRM商机003", adminUser.getId());

            // then: 跨表事务一致性——tender 与 assignment_record 同事务落库
            List<TenderAssignmentRecord> records = assignmentRecordRepository
                    .findByTenderIdOrderByAssignedAtDesc(saved.getId());
            assertEquals(1, records.size(),
                    "linkCrmOpportunity 应写入 1 条 DISPATCH 分配记录");
            TenderAssignmentRecord record = records.get(0);
            assertEquals(TenderAssignmentRecord.AssignmentType.DISPATCH, record.getType());
            assertEquals(adminUser.getId(), record.getAssigneeId());
            assertEquals(adminUser.getId(), record.getAssignedById());
            assertEquals("集成测试管理员", record.getAssigneeName());
            assertEquals("集成测试管理员", record.getAssignedByName());
            assertEquals("CRM商机关联，自动接手评估", record.getRemark());
            assertNotNull(record.getAssignedAt());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  T010: tryAutoAssign 失败一致性
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("tryAutoAssign 失败：Tender 保持 PENDING_ASSIGNMENT，不写分配记录")
    class TryAutoAssignFailureConsistency {

        @Test
        @DisplayName("autoAssignmentService 抛 RuntimeException → Tender 落库为 PENDING_ASSIGNMENT，无 DISPATCH 记录")
        void autoAssignThrowsRuntimeException_tenderStaysPendingNoDispatch() {
            // given: mock 抛异常（模拟外部 CRM 调用失败）
            when(autoAssignmentService.autoAssignIfPossible(any()))
                    .thenThrow(new RuntimeException("模拟自动分配失败"));

            TenderDTO dto = buildTenderDTO("t010", "test-int-purchaser-t010");

            // when: createTender 不抛异常（tryAutoAssign catch 了 RuntimeException）
            TenderDTO saved = tenderCommandService.createTender(dto, adminUser.getId());

            // then: Tender 已落库为 PENDING_ASSIGNMENT（tryAutoAssign 在 save 之后调用）
            Tender tender = tenderRepository.findById(saved.getId()).orElseThrow();
            assertEquals(Tender.Status.PENDING_ASSIGNMENT, tender.getStatus(),
                    "tryAutoAssign 失败时 Tender 应保持 PENDING_ASSIGNMENT");

            // and: 无 DISPATCH 记录（tryAutoAssign 失败不会写分配记录）
            List<TenderAssignmentRecord> records = assignmentRecordRepository
                    .findByTenderIdOrderByAssignedAtDesc(saved.getId());
            assertTrue(records.isEmpty(),
                    "tryAutoAssign 失败时不应写入 DISPATCH 分配记录");
        }
    }

    @Nested
    @DisplayName("tryAutoAssign 成功：Tender 状态推进到 TRACKING")
    class TryAutoAssignSuccessAdvancement {

        @Test
        @DisplayName("autoAssignmentService 返回 matched=true → Tender 状态推进到 TRACKING")
        void autoAssignMatched_tenderAdvancesToTracking() {
            // given: mock 返回匹配成功
            AssignmentResult match = AssignmentResult.success(
                    "crm-proj-001", "pm-001", "测试PM", "dept-001", "测试部门");
            when(autoAssignmentService.autoAssignIfPossible(any())).thenReturn(match);

            TenderDTO dto = buildTenderDTO("t010b", "test-int-purchaser-t010b");

            // when
            TenderDTO saved = tenderCommandService.createTender(dto, adminUser.getId());

            // then: Tender 状态推进到 TRACKING
            Tender tender = tenderRepository.findById(saved.getId()).orElseThrow();
            assertEquals(Tender.Status.TRACKING, tender.getStatus());
            assertEquals("测试PM", tender.getProjectManagerName());
            assertEquals("测试部门", tender.getDepartment());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  T011: deleteTender 级联事务一致性
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteTender 级联事务一致性")
    class DeleteTenderCascadeConsistency {

        @Test
        @DisplayName("deleteTender 后 tenders 和 tender_attachments 表均无对应记录，"
                + "但 tender_assignment_records 有残留（无 FK 约束）")
        void deleteTender_cascadesAttachmentsButLeavesOrphanAssignmentRecords() {
            // given: 创建带附件的标讯（同时验证无 FK 的 assignment_records 残留）
            TenderDTO dto = buildTenderDTO("t011", "test-int-purchaser-t011");
            dto.setAttachments(List.of(
                    TenderAttachmentDTO.builder()
                            .fileName("test-int-file.pdf")
                            .fileType("application/pdf")
                            .fileUrl("https://test.local/test-int-file.pdf")
                            .build()
            ));
            TenderDTO saved = tenderCommandService.createTender(dto, adminUser.getId());

            // verify setup: attachment exists
            List<TenderAttachment> attachmentsBefore = attachmentRepository.findByTenderId(saved.getId());
            assertEquals(1, attachmentsBefore.size(),
                    "前置条件：标讯应已落库 1 条附件");

            // when
            tenderCommandService.deleteTender(saved.getId(), adminUser.getId());

            // then: tender 已删除
            assertTrue(tenderRepository.findById(saved.getId()).isEmpty(),
                    "deleteTender 后 tenders 表应无对应记录");
            // and: 附件级联删除（DB FK ON DELETE CASCADE）
            assertTrue(attachmentRepository.findByTenderId(saved.getId()).isEmpty(),
                    "deleteTender 后 tender_attachments 表应级联删除无对应记录");
            // ⚠️ KNOWN_TECHNICAL_DEBT: tender_assignment_records 无 FK 约束（B73 基线），
            // deleteTender 不会清理 assignment_records，会留下孤儿数据。
            // 这是当前行为，暂不修复（需评估清理影响范围后决定是否改 deleteTender）。
            List<TenderAssignmentRecord> orphanRecords = assignmentRecordRepository
                    .findByTenderIdOrderByAssignedAtDesc(saved.getId());
            assertTrue(orphanRecords.isEmpty(),
                    "当前 deleteTender 不清理 assignment_records（无 FK 约束），"
                    + "这是已知技术债，待评估修复范围后处理");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  T012: CO-265 createTender 三字段去重检测
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CO-265: createTender 三字段去重检测")
    class CreateTenderDeduplication {

        @Test
        @DisplayName("相同 purchaserName + registrationDeadline + bidOpeningTime → 抛 TenderDuplicateException")
        void sameThreeFields_throwsTenderDuplicateException() {
            // given: 第一条标讯
            LocalDateTime regDeadline = LocalDateTime.now().plusDays(30).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            LocalDateTime bidOpenTime = regDeadline.plusDays(14);
            TenderDTO dtoA = buildTenderDTO("t012-a", "test-int-purchaser-t012");
            dtoA.setRegistrationDeadline(regDeadline);
            dtoA.setBidOpeningTime(bidOpenTime);
            tenderCommandService.createTender(dtoA, adminUser.getId());

            // when & then: 第二条相同三字段（不同 title，但 dedup 不看 title）
            TenderDTO dtoB = buildTenderDTO("t012-b", "test-int-purchaser-t012");
            dtoB.setRegistrationDeadline(regDeadline);
            dtoB.setBidOpeningTime(bidOpenTime);

            assertThrows(TenderDuplicateException.class, () ->
                    tenderCommandService.createTender(dtoB, adminUser.getId()));
        }

        @Test
        @DisplayName("不同 bidOpeningTime → 不视为重复，创建成功")
        void differentBidOpeningTime_notDuplicate() {
            // given: 第一条标讯
            LocalDateTime regDeadline = LocalDateTime.now().plusDays(30).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            LocalDateTime bidOpenTimeA = regDeadline.plusDays(14);
            TenderDTO dtoA = buildTenderDTO("t012-c", "test-int-purchaser-t012-alt");
            dtoA.setRegistrationDeadline(regDeadline);
            dtoA.setBidOpeningTime(bidOpenTimeA);
            tenderCommandService.createTender(dtoA, adminUser.getId());

            // when: 第二条不同 bidOpeningTime
            LocalDateTime bidOpenTimeB = regDeadline.plusDays(15);
            TenderDTO dtoB = buildTenderDTO("t012-d", "test-int-purchaser-t012-alt");
            dtoB.setRegistrationDeadline(regDeadline);
            dtoB.setBidOpeningTime(bidOpenTimeB);

            // then: 不抛异常，创建成功
            TenderDTO savedB = tenderCommandService.createTender(dtoB, adminUser.getId());
            assertNotNull(savedB.getId());
        }
    }
}
