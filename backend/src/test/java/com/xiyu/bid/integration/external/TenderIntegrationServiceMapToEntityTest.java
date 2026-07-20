package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TenderIntegrationMapper#toEntity} 的单元测试。
 */
class TenderIntegrationServiceMapToEntityTest {

    private TenderIntegrationMapper mapper;
    private ProjectManagerIdResolver managerIdResolver;

    @BeforeEach
    void setUp() {
        managerIdResolver = mock(ProjectManagerIdResolver.class);
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                mock(TenderEvaluationRepository.class),
                mock(TenderEvaluationSubmissionMapper.class));
        mapper = new TenderIntegrationMapper(
                mock(TenderMapper.class),
                evaluationMapper,
                managerIdResolver);
    }

    private TenderPushRequest baseRequest() {
        TenderPushRequest r = new TenderPushRequest();
        r.setTitle("测试标讯");
        return r;
    }

    @Test
    @DisplayName("带 crmId → 来源=CRM创建，状态=已评估")
    void toEntity_withCrmId_setsCrmSourceAndEvaluatedStatus() {
        TenderPushRequest r = baseRequest();
        r.setCrmId("CC001");

        Tender t = mapper.toEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.CRM_OPPORTUNITY);
        assertThat(t.getSource()).isEqualTo("CRM创建");
        assertThat(t.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("不带 crmId → 来源=第三方平台，状态=待分配")
    void toEntity_withoutCrmId_setsExternalSourceAndPendingStatus() {
        TenderPushRequest r = baseRequest();

        Tender t = mapper.toEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getSource()).isEqualTo("第三方平台");
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    @Test
    @DisplayName("crmId 为空字符串 → 视为非 CRM 转入")
    void toEntity_emptyCrmId_treatedAsNonCrm() {
        TenderPushRequest r = baseRequest();
        r.setCrmId("");

        Tender t = mapper.toEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    @Test
    @DisplayName("crmId 为空白字符 → 视为非 CRM 转入")
    void toEntity_blankCrmId_treatedAsNonCrm() {
        TenderPushRequest r = baseRequest();
        r.setCrmId("   ");

        Tender t = mapper.toEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    @Test
    @DisplayName("CO-333: projectManagerName 唯一匹配用户时，同步写入 projectManagerId（可见性锚点）")
    void toEntity_withProjectManagerName_resolvesUserId() {
        TenderPushRequest r = baseRequest();
        r.setProjectManagerName("韩超");
        // mock applyTo 副作用：写入姓名 + user_id（模拟 resolver 命中）
        doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setProjectManagerName("韩超");
            t.setProjectManagerId(25L);
            return null;
        }).when(managerIdResolver).applyTo(any(Tender.class), eq(null), eq("韩超"));

        Tender t = mapper.toEntity(r);

        assertThat(t.getProjectManagerName()).isEqualTo("韩超");
        assertThat(t.getProjectManagerId()).isEqualTo(25L);
    }

    @Test
    @DisplayName("CO-333: projectManagerName 无匹配用户时，projectManagerId 保持 null（不阻断）")
    void toEntity_withProjectManagerName_noMatch_keepsNullId() {
        TenderPushRequest r = baseRequest();
        r.setProjectManagerName("不存在的人");
        // mock applyTo 副作用：写入姓名但 user_id 保持 null（模拟 resolver 无匹配）
        doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setProjectManagerName("不存在的人");
            return null;
        }).when(managerIdResolver).applyTo(any(Tender.class), eq(null), eq("不存在的人"));

        Tender t = mapper.toEntity(r);

        assertThat(t.getProjectManagerName()).isEqualTo("不存在的人");
        assertThat(t.getProjectManagerId()).isNull();
    }

    @Test
    @DisplayName("CO-333: projectManagerName 重名时跳过 id 绑定避免误绑")
    void toEntity_withProjectManagerName_duplicateName_skipsIdBinding() {
        TenderPushRequest r = baseRequest();
        r.setProjectManagerName("张伟");
        doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setProjectManagerName("张伟");
            return null;
        }).when(managerIdResolver).applyTo(any(Tender.class), eq(null), eq("张伟"));

        Tender t = mapper.toEntity(r);

        assertThat(t.getProjectManagerName()).isEqualTo("张伟");
        assertThat(t.getProjectManagerId()).isNull();
    }

    @Test
    @DisplayName("CO-333: applyUpdate 同步更新 projectManagerName + projectManagerId")
    void applyUpdate_withProjectManagerName_resolvesUserId() {
        TenderPushRequest r = baseRequest();
        r.setProjectManagerName("李四");
        doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setProjectManagerName("李四");
            t.setProjectManagerId(30L);
            return null;
        }).when(managerIdResolver).applyTo(any(Tender.class), eq(null), eq("李四"));
        Tender existing = new Tender();

        mapper.applyUpdate(existing, r);

        assertThat(existing.getProjectManagerName()).isEqualTo("李四");
        assertThat(existing.getProjectManagerId()).isEqualTo(30L);
        assertThat(existing.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("CO-283: toDownloadUrl 对 doc-insight:// URL 转换为下载地址")
    void toDownloadUrl_docInsightUrl_convertsToDownloadUrl() {
        String url = mapper.toDownloadUrl("doc-insight://TENDER_INTAKE/abc/file.pdf");

        assertThat(url).isEqualTo("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2FTENDER_INTAKE%2Fabc%2Ffile.pdf");
    }

    @Test
    @DisplayName("CO-283: toDownloadUrl 对已转换的下载地址保持幂等，避免双重嵌套")
    void toDownloadUrl_alreadyDownloadUrl_returnsAsIs() {
        String existing = "/api/doc-insight/download?fileUrl=https%3A%2F%2Fcrm.ehsy.com%2Ffile.pdf";

        String url = mapper.toDownloadUrl(existing);

        assertThat(url).isEqualTo(existing);
    }

    @Test
    @DisplayName("CO-283: toDownloadUrl 对普通外部 URL 原样返回")
    void toDownloadUrl_externalUrl_returnsAsIs() {
        String external = "https://crm.ehsy.com/attachment/file.pdf";

        String url = mapper.toDownloadUrl(external);

        assertThat(url).isEqualTo(external);
    }

    @Test
    @DisplayName("CO-283: toDownloadUrl 对空值保持空值")
    void toDownloadUrl_nullOrBlank_returnsAsIs() {
        assertThat(mapper.toDownloadUrl(null)).isNull();
        assertThat(mapper.toDownloadUrl("")).isEmpty();
        assertThat(mapper.toDownloadUrl("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("dueDate 仅传日期时应按外部接口契约返回参数错误")
    void toEntity_dueDateDateOnly_shouldRejectWithFieldMessage() {
        TenderPushRequest r = baseRequest();
        r.setDueDate("2026-12-31");

        assertThatThrownBy(() -> mapper.toEntity(r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dueDate 格式错误，应为 yyyy-MM-ddTHH:mm 或 yyyy-MM-ddTHH:mm:ss");
    }

    @Test
    @DisplayName("dueDate 支持到分钟和到秒格式")
    void toEntity_dueDateDateTime_shouldAcceptMinuteAndSecondPrecision() {
        TenderPushRequest minute = baseRequest();
        minute.setDueDate("2026-12-31T23:59");
        TenderPushRequest second = baseRequest();
        second.setDueDate("2026-12-31T23:59:30");

        assertThat(mapper.toEntity(minute).getDeadline()).hasToString("2026-12-31T23:59");
        assertThat(mapper.toEntity(second).getDeadline()).hasToString("2026-12-31T23:59:30");
    }

    @Test
    @DisplayName("parseDateTime null 和空白字符串返回 null")
    void parseDateTime_nullAndBlank_returnsNull() {
        assertThat(TenderIntegrationMapper.parseDateTime("test", null)).isNull();
        assertThat(TenderIntegrationMapper.parseDateTime("test", "")).isNull();
        assertThat(TenderIntegrationMapper.parseDateTime("test", "   ")).isNull();
    }

    @Test
    @DisplayName("parseDateTime 空格分隔的日期时间自动替换为 T")
    void parseDateTime_spaceSeparator_replacedWithT() {
        var result = TenderIntegrationMapper.parseDateTime("test", "2026-06-20 10:30");
        assertThat(result).hasToString("2026-06-20T10:30");
    }

    @Test
    @DisplayName("parseDateTime 分钟级格式自动补全秒")
    void parseDateTime_minutePrecision_autoCompletesSeconds() {
        var result = TenderIntegrationMapper.parseDateTime("test", "2026-06-20T10:30");
        assertThat(result).hasToString("2026-06-20T10:30");
    }

    @Test
    @DisplayName("parseDateTime 秒级格式正常解析")
    void parseDateTime_secondPrecision_parsesCorrectly() {
        var result = TenderIntegrationMapper.parseDateTime("test", "2026-06-20T10:30:45");
        assertThat(result).hasToString("2026-06-20T10:30:45");
    }

    @Test
    @DisplayName("parseDateTime 格式错误时错误消息包含字段名")
    void parseDateTime_invalidFormat_messageContainsFieldName() {
        assertThatThrownBy(() -> TenderIntegrationMapper.parseDateTime("bidOpeningTime", "2026-13-32T25:60"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bidOpeningTime");
    }

    @Test
    @DisplayName("parseDateTime 仅日期格式被拒绝")
    void parseDateTime_dateOnly_rejected() {
        assertThatThrownBy(() -> TenderIntegrationMapper.parseDateTime("dueDate", "2026-12-31"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dueDate");
    }
}
