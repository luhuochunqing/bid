package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.CaseExportContext;
import com.xiyu.bid.casework.domain.model.CaseExportZipEntry;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaseExportPolicy 单元测试。
 *
 * <p>纯核心测试：无 Spring 上下文，无数据库，直接实例化 @Component 策略类。
 */
class CaseExportPolicyTest {

    private CaseExportPolicy policy;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 9, 10, 30, 0);

    @BeforeEach
    void setUp() {
        policy = new CaseExportPolicy();
    }

    // ---------------------------------------------------------------
    // validateExportRequest
    // ---------------------------------------------------------------

    @Test
    @DisplayName("validateExportRequest: null 输入返回失败")
    void validate_NullInput_ReturnsFailure() {
        CaseExportPolicy.ExportValidationResult result = policy.validateExportRequest(null);
        assertFalse(result.valid());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("validateExportRequest: 空列表返回失败")
    void validate_EmptyList_ReturnsFailure() {
        CaseExportPolicy.ExportValidationResult result = policy.validateExportRequest(List.of());
        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("没有可导出的案例"));
    }

    @Test
    @DisplayName("validateExportRequest: 超过 500 条返回失败")
    void validate_Over500_ReturnsFailure() {
        List<KnowledgeCase> oversized = java.util.stream.Stream.generate(this::createCase)
                .limit(501)
                .toList();

        CaseExportPolicy.ExportValidationResult result = policy.validateExportRequest(oversized);
        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("超过限制"));
        assertTrue(result.errorMessage().contains("500"));
    }

    @Test
    @DisplayName("validateExportRequest: 有效输入返回成功")
    void validate_ValidInput_ReturnsSuccess() {
        List<KnowledgeCase> valid = java.util.stream.Stream.generate(this::createCase)
                .limit(5)
                .toList();

        CaseExportPolicy.ExportValidationResult result = policy.validateExportRequest(valid);
        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    // ---------------------------------------------------------------
    // generateZipFileName
    // ---------------------------------------------------------------

    @Test
    @DisplayName("generateZipFileName: 生成正确文件名格式")
    void generateZipFileName_ReturnsCorrectFormat() {
        String fileName = policy.generateZipFileName();
        assertNotNull(fileName);
        assertTrue(fileName.startsWith("方案管理-案例库文件包-"));
        assertTrue(fileName.endsWith(".zip"));
    }

    // ---------------------------------------------------------------
    // sortCasesForExport
    // ---------------------------------------------------------------

    @Test
    @DisplayName("sortCasesForExport: 置顶优先，非置顶按项目名升序，再按创建时间降序")
    void sortCases_OrdersCorrectly() {
        KnowledgeCase pinned = createCase();
        pinned.setIsPinned(true);
        pinned.setSourceProjectName("A项目");
        pinned.setScoringPointTitle("标题X");
        pinned.setCreatedAt(LocalDateTime.of(2026, 6, 1, 0, 0));

        KnowledgeCase notPinnedA = createCase();
        notPinnedA.setIsPinned(false);
        notPinnedA.setSourceProjectName("A项目");
        notPinnedA.setScoringPointTitle("标题A");
        notPinnedA.setCreatedAt(LocalDateTime.of(2026, 6, 3, 0, 0));

        KnowledgeCase notPinnedB = createCase();
        notPinnedB.setIsPinned(false);
        notPinnedB.setSourceProjectName("B项目");
        notPinnedB.setScoringPointTitle("标题B");
        notPinnedB.setCreatedAt(LocalDateTime.of(2026, 6, 2, 0, 0));

        List<KnowledgeCase> result = policy.sortCasesForExport(List.of(notPinnedA, notPinnedB, pinned));

        assertEquals(3, result.size());
        assertTrue(result.get(0).getIsPinned());  // 置顶排第一
        assertFalse(result.get(1).getIsPinned());
        assertFalse(result.get(2).getIsPinned());
    }

    // ---------------------------------------------------------------
    // buildZipEntries
    // ---------------------------------------------------------------

    @Test
    @DisplayName("buildZipEntries: 每个案例生成两项 ZIP 条目（应答全文 + 索引信息）")
    void buildZipEntries_EachCaseGeneratesTwoEntries() {
        KnowledgeCase kc = createCase();
        kc.setSourceProjectName("测试项目");
        kc.setScoringPointTitle("评分项A");
        kc.setResponseText("应答全文内容");

        List<CaseExportZipEntry> entries = policy.buildZipEntries(List.of(kc));

        assertEquals(2, entries.size());

        boolean hasResponseTxt = entries.stream().anyMatch(e ->
                e.entryPath().endsWith("应答全文.txt"));
        boolean hasIndexTxt = entries.stream().anyMatch(e ->
                e.entryPath().endsWith("案例索引信息.txt"));

        assertTrue(hasResponseTxt);
        assertTrue(hasIndexTxt);
    }

    @Test
    @DisplayName("buildZipEntries: 安全文件名规范化")
    void buildZipEntries_SanitizesFileNames() {
        KnowledgeCase kc = createCase();
        kc.setSourceProjectName("测试/项目:名称");
        kc.setScoringPointTitle("评分*项?A");

        List<CaseExportZipEntry> entries = policy.buildZipEntries(List.of(kc));

        assertFalse(entries.isEmpty());
        for (CaseExportZipEntry entry : entries) {
            assertThat(entry.entryPath()).doesNotContain(":").doesNotContain("*").doesNotContain("?");
        }
    }

    @Test
    @DisplayName("buildZipEntries: 空列表返回空")
    void buildZipEntries_EmptyList_ReturnsEmpty() {
        List<CaseExportZipEntry> entries = policy.buildZipEntries(List.of());
        assertTrue(entries.isEmpty());
    }

    // ---------------------------------------------------------------
    // buildResponseTextContent
    // ---------------------------------------------------------------

    @Test
    @DisplayName("buildResponseTextContent: 包含关键字段")
    void buildResponseTextContent_ContainsKeyFields() {
        KnowledgeCase kc = createCase();
        kc.setSourceProjectName("测试项目");
        kc.setScoringPointTitle("技术方案");
        kc.setScoringCategory("技术");
        kc.setCustomerType("国有企业");
        kc.setProjectType("综合");
        kc.setBidResult("WON");
        kc.setProductLine("产品线A");
        kc.setRequirementRaw("需求：需要技术方案设计");
        kc.setResponseText("我们提供微服务架构方案");

        String content = policy.buildResponseTextContent(kc);

        assertTrue(content.contains("测试项目"));
        assertTrue(content.contains("技术方案"));
        assertTrue(content.contains("技术"));
        assertTrue(content.contains("国有企业"));
        assertTrue(content.contains("综合"));
        assertTrue(content.contains("WON"));
        assertTrue(content.contains("产品线A"));
        assertTrue(content.contains("需求：需要技术方案设计"));
        assertTrue(content.contains("我们提供微服务架构方案"));
    }

    @Test
    @DisplayName("buildResponseTextContent: null 安全转换")
    void buildResponseTextContent_NullSafe() {
        KnowledgeCase kc = createCase();
        kc.setSourceProjectName(null);
        kc.setScoringCategory(null);
        kc.setCustomerType(null);
        kc.setProductLine(null);
        kc.setBidResult(null);
        kc.setRequirementRaw(null);
        kc.setResponseText(null);
        kc.setCreatedAt(null);

        String content = policy.buildResponseTextContent(kc);
        assertNotNull(content);
        assertTrue(content.contains("-"));  // nullSafe 用 "-" 代替 null
    }

    // ---------------------------------------------------------------
    // buildIndexContent
    // ---------------------------------------------------------------

    @Test
    @DisplayName("buildIndexContent: 包含所有索引字段")
    void buildIndexContent_ContainsAllFields() {
        KnowledgeCase kc = createCase();
        kc.setId(42L);
        kc.setSourceProjectId(100L);
        kc.setSourceProjectName("测试项目");
        kc.setScoringPointTitle("技术方案");
        kc.setScoringCategory("技术");
        kc.setCustomerType("国有企业");
        kc.setProjectType("综合");
        kc.setBidResult("WON");
        kc.setProductLine("产品线A");
        kc.setReuseCount(3);
        kc.setIsPinned(true);

        String content = policy.buildIndexContent(kc);

        assertTrue(content.contains("42"));
        assertTrue(content.contains("100"));
        assertTrue(content.contains("测试项目"));
        assertTrue(content.contains("技术方案"));
        assertTrue(content.contains("技术"));
        assertTrue(content.contains("国有企业"));
        assertTrue(content.contains("综合"));
        assertTrue(content.contains("WON"));
        assertTrue(content.contains("产品线A"));
        assertTrue(content.contains("3"));
        assertTrue(content.contains("是"));  // isPinned 显示"是"
    }

    // ---------------------------------------------------------------
    // calculateTotalExportSize
    // ---------------------------------------------------------------

    @Test
    @DisplayName("calculateTotalExportSize: 计算总和正确")
    void calculateTotalSize_SumsCorrectly() {
        List<CaseExportZipEntry> entries = List.of(
                new CaseExportZipEntry("a.txt", new byte[100], 100),
                new CaseExportZipEntry("b.txt", new byte[200], 200),
                new CaseExportZipEntry("c.txt", new byte[300], 300)
        );

        assertEquals(600, policy.calculateTotalExportSize(entries));
    }

    @Test
    @DisplayName("calculateTotalExportSize: 空列表返回0")
    void calculateTotalSize_Empty_ReturnsZero() {
        assertEquals(0, policy.calculateTotalExportSize(List.of()));
    }

    // ---------------------------------------------------------------
    // buildExportContext
    // ---------------------------------------------------------------

    @Test
    @DisplayName("buildExportContext: 构建完整上下文")
    void buildExportContext_BuildsCorrectContext() {
        KnowledgeCase kc = createCase();
        List<KnowledgeCase> cases = List.of(kc);

        CaseExportContext context = policy.buildExportContext(cases, "操作员A");

        assertNotNull(context);
        assertEquals(cases, context.cases());
        assertFalse(context.zipEntries().isEmpty());
        assertTrue(context.zipFileName().endsWith(".zip"));
        assertEquals("操作员A", context.operatorName());
        assertNotNull(context.exportTime());
    }

    // ---------------------------------------------------------------
    // 辅助方法
    // ---------------------------------------------------------------

    private KnowledgeCase createCase() {
        KnowledgeCase kc = new KnowledgeCase();
        kc.setId(1L);
        kc.setSourceProjectId(100L);
        kc.setSourceProjectName("默认项目");
        kc.setScoringPointTitle("默认评分项");
        kc.setScoringCategory("技术");
        kc.setCustomerType("国有企业");
        kc.setProjectType("综合");
        kc.setBidResult("WON");
        kc.setProductLine("默认产品线");
        kc.setRequirementRaw("默认需求原文");
        kc.setResponseText("默认应答全文");
        kc.setReuseCount(1);
        kc.setIsPinned(false);
        kc.setStatus("ACTIVE");
        kc.setCreatedAt(NOW);
        return kc;
    }
}
