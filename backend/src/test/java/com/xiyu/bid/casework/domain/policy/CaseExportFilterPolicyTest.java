package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.CaseExportCriteria;
import com.xiyu.bid.casework.domain.model.CaseExportRecord;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaseExportFilterPolicy 单元测试。
 *
 * <p>纯核心测试：无 Spring 上下文，无数据库，所有方法均为静态纯函数。
 */
class CaseExportFilterPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 9, 10, 0, 0);

    @Test
    @DisplayName("filterCases: null 输入返回空列表")
    void filterCases_NullInput_ReturnsEmpty() {
        assertTrue(CaseExportFilterPolicy.filterCases(null, null).isEmpty());
    }

    @Test
    @DisplayName("filterCases: 空列表返回空列表")
    void filterCases_EmptyList_ReturnsEmpty() {
        assertTrue(CaseExportFilterPolicy.filterCases(List.of(), null).isEmpty());
    }

    @Test
    @DisplayName("filterCases: 过滤非 ACTIVE 状态")
    void filterCases_FiltersNonActive_ExcludesInactive() {
        KnowledgeCase active = createActiveCase("标题A", "技术", "国有企业", "综合", "需求内容");
        KnowledgeCase offShelf = createCaseWithStatus("标题B", "OFF_SHELF");

        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(active, offShelf), null);
        assertEquals(1, result.size());
        assertEquals("标题A", result.get(0).getScoringPointTitle());
    }

    @Test
    @DisplayName("filterCases: 无条件过滤仅保留 ACTIVE")
    void filterCases_NullCriteria_ReturnsAllActive() {
        KnowledgeCase c1 = createActiveCase("标题A", "技术", "国有企业", "综合", "需求内容");
        KnowledgeCase c2 = createActiveCase("标题B", "商务", "民营企业", "工程", "其他需求");

        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(c1, c2), null);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("filterCases: 按关键字过滤（匹配标题）")
    void filterCases_KeywordMatchesTitle_ReturnsFiltered() {
        KnowledgeCase matching = createActiveCase("技术方案设计", "技术", "国有企业", "综合", "需求内容");
        KnowledgeCase nonMatching = createActiveCase("商务报价", "商务", "民营企业", "工程", "其他");

        CaseExportCriteria criteria = new CaseExportCriteria("技术方案", null, null, null, null, null, null, null, null);
        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(matching, nonMatching), criteria);
        assertEquals(1, result.size());
        assertEquals("技术方案设计", result.get(0).getScoringPointTitle());
    }

    @Test
    @DisplayName("filterCases: 按关键字过滤（匹配需求原文）")
    void filterCases_KeywordMatchesRequirement_ReturnsFiltered() {
        KnowledgeCase matching = createActiveCase("标题A", "技术", "国有企业", "综合", "本项目需要防火墙设备");
        KnowledgeCase nonMatching = createActiveCase("标题B", "商务", "民营企业", "工程", "简单需求");
        CaseExportCriteria criteria = new CaseExportCriteria("防火墙", null, null, null, null, null, null, null, null);

        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(matching, nonMatching), criteria);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("filterCases: 按关键字过滤（匹配应答全文）")
    void filterCases_KeywordMatchesResponse_ReturnsFiltered() {
        KnowledgeCase matching = createActiveCase("标题A", "技术", "国有企业", "综合", "普通需求");
        matching.setResponseText("我们采用微服务架构进行系统设计");
        KnowledgeCase nonMatching = createActiveCase("标题B", "商务", "民营企业", "工程", "其他内容");
        CaseExportCriteria criteria = new CaseExportCriteria("微服务架构", null, null, null, null, null, null, null, null);

        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(matching, nonMatching), criteria);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("filterCases: 关键字大小写不敏感")
    void filterCases_KeywordCaseInsensitive_ReturnsFiltered() {
        KnowledgeCase matchTitle = createActiveCase("Technical Design", null, null, null, "需求内容");
        CaseExportCriteria criteria = new CaseExportCriteria("technical", null, null, null, null, null, null, null, null);

        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(matchTitle), criteria);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("filterCases: 按评分类别过滤")
    void filterCases_FiltersByScoringCategory() {
        KnowledgeCase c1 = createActiveCase("标题A", "技术", "国有企业", "综合", "需求");
        KnowledgeCase c2 = createActiveCase("标题B", "商务", "国有企业", "综合", "需求");
        CaseExportCriteria criteria = new CaseExportCriteria(null, "技术", null, null, null, null, null, null, null);

        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(c1, c2), criteria);
        assertEquals(1, result.size());
        assertEquals("标题A", result.get(0).getScoringPointTitle());
    }

    @Test
    @DisplayName("filterCases: 按客户类型过滤")
    void filterCases_FiltersByCustomerType() {
        KnowledgeCase c1 = createActiveCase("标题A", "技术", "国有企业", "综合", "需求");
        KnowledgeCase c2 = createActiveCase("标题B", "技术", "民营企业", "综合", "需求");
        CaseExportCriteria criteria = new CaseExportCriteria(null, null, "民营企业", null, null, null, null, null, null);

        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(c1, c2), criteria);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("filterCases: 按项目类型过滤（多选）")
    void filterCases_FiltersByProjectTypes() {
        KnowledgeCase c1 = createActiveCase("标题A", "技术", "国有企业", "综合", "需求");
        KnowledgeCase c2 = createActiveCase("标题B", "技术", "国有企业", "工程", "需求");
        CaseExportCriteria criteria = new CaseExportCriteria(null, null, null, List.of("综合"), null, null, null, null, null);

        List<KnowledgeCaseReadModel> result = CaseExportFilterPolicy.filterCases(List.of(c1, c2), criteria);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("toExportRecord: null 输入返回空记录")
    void toExportRecord_NullInput_ReturnsEmptyRecord() {
        CaseExportRecord record = CaseExportFilterPolicy.toExportRecord(null);
        assertEquals("", record.scoringPointTitle());
        assertEquals("", record.sourceProjectName());
        assertEquals(0, record.reuseCount());
        assertEquals("", record.responseSummary());
    }

    @Test
    @DisplayName("toExportRecord: 正常映射所有字段")
    void toExportRecord_ValidInput_MapsAllFields() {
        KnowledgeCase kc = createActiveCase("评分项A", "技术", "国有企业", "综合", "需求内容");
        kc.setSourceProjectName("测试项目");
        kc.setReuseCount(5);
        kc.setBidResult("WON");
        kc.setResponseText("这是应答全文内容");
        kc.setCreatedAt(NOW);

        CaseExportRecord record = CaseExportFilterPolicy.toExportRecord(kc);
        assertEquals("评分项A", record.scoringPointTitle());
        assertEquals("测试项目", record.sourceProjectName());
        assertEquals("综合", record.projectType());
        assertEquals("国有企业", record.customerType());
        assertEquals("技术", record.scoringCategory());
        assertEquals("WON", record.bidResult());
        assertEquals(5, record.reuseCount());
        assertEquals("2026-06-09 10:00", record.createdAt());
    }

    @Test
    @DisplayName("toExportRecord: null 字段转为空字符串或0")
    void toExportRecord_NullFields_DefaultsCorrectly() {
        KnowledgeCase kc = createActiveCase("标题", null, null, null, null);
        kc.setReuseCount(null);
        kc.setCreatedAt(null);
        kc.setSourceProjectName(null);

        CaseExportRecord record = CaseExportFilterPolicy.toExportRecord(kc);
        assertEquals("", record.scoringCategory());
        assertEquals("", record.projectType());
        assertEquals("", record.customerType());
        assertEquals("", record.bidResult());
        assertEquals("", record.sourceProjectName());
        assertEquals("", record.createdAt());
        assertEquals(0, record.reuseCount());
    }

    @Test
    @DisplayName("summarizeResponse: 空/null 返回空字符串")
    void summarizeResponse_NullOrBlank_ReturnsEmpty() {
        KnowledgeCase kc1 = createActiveCase("标题", null, null, null, null);
        kc1.setResponseText(null);
        assertEquals("", CaseExportFilterPolicy.toExportRecord(kc1).responseSummary());

        KnowledgeCase kc2 = createActiveCase("标题", null, null, null, null);
        kc2.setResponseText("   ");
        assertEquals("", CaseExportFilterPolicy.toExportRecord(kc2).responseSummary());
    }

    @Test
    @DisplayName("summarizeResponse: 短文本原样返回")
    void summarizeResponse_ShortText_ReturnsAsIs() {
        KnowledgeCase kc = createActiveCase("标题", null, null, null, null);
        kc.setResponseText("这是短文本。");
        assertEquals("这是短文本。", CaseExportFilterPolicy.toExportRecord(kc).responseSummary());
    }

    @Test
    @DisplayName("summarizeResponse: 200 字以上截断加省略号")
    void summarizeResponse_LongText_TruncatesWithEllipsis() {
        String longText = "字".repeat(210);
        KnowledgeCase kc = createActiveCase("标题", null, null, null, null);
        kc.setResponseText(longText);
        String summary = CaseExportFilterPolicy.toExportRecord(kc).responseSummary();
        assertTrue(summary.endsWith("..."));
        assertEquals(203, summary.length());
    }

    @Test
    @DisplayName("summarizeResponse: 正好 200 字不截断")
    void summarizeResponse_Exact200Chars_NotTruncated() {
        String text = "字".repeat(200);
        KnowledgeCase kc = createActiveCase("标题", null, null, null, null);
        kc.setResponseText(text);
        String summary = CaseExportFilterPolicy.toExportRecord(kc).responseSummary();
        assertEquals(200, summary.length());
    }

    private static KnowledgeCase createActiveCase(
            String title, String category, String customerType,
            String projectType, String requirementRaw) {
        KnowledgeCase kc = new KnowledgeCase();
        kc.setId(1L);
        kc.setSourceProjectId(100L);
        kc.setSourceProjectName("默认项目");
        kc.setScoringPointTitle(title != null ? title : "默认评分项");
        kc.setScoringCategory(category);
        kc.setCustomerType(customerType);
        kc.setProjectType(projectType);
        kc.setRequirementRaw(requirementRaw != null ? requirementRaw : "默认需求内容");
        kc.setResponseText("默认应答全文");
        kc.setReuseCount(0);
        kc.setIsPinned(false);
        kc.setStatus("ACTIVE");
        kc.setCreatedAt(NOW);
        return kc;
    }

    private static KnowledgeCase createCaseWithStatus(String title, String status) {
        KnowledgeCase kc = createActiveCase(title, null, null, null, null);
        kc.setStatus(status);
        return kc;
    }
}
