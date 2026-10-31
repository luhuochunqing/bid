// Input: 资质匹配穿透场景（等级向下兼容/等级不足拦截/目录页过滤/占位模板拦截）
// Output: 验证 QualificationMatchPolicy 在真实招投标复杂场景下的穿透核验与硬信号拦截
// Pos: compliance/domain/policy/ - 资质匹配穿透核验单元测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.entity.Qualification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationMatchPenetrationTest {

    @Test
    @DisplayName("等级判定：持有特级/一级满足二级及以上要求，持有三级明确提示等级不足")
    void testBuildDetails_GradeHierarchySatisfactionAndRejection() {
        LocalDate today = LocalDate.of(2026, 9, 6);
        List<Qualification> certs = List.of(
                Qualification.builder()
                        .id(1L)
                        .name("建筑机电安装工程专业承包一级")
                        .level("一级")
                        .expiryDate(LocalDate.of(2028, 1, 1))
                        .build(),
                Qualification.builder()
                        .id(2L)
                        .name("电力工程施工总承包三级")
                        .level("三级")
                        .expiryDate(LocalDate.of(2028, 1, 1))
                        .build()
        );

        List<DocumentContent.PageContent> pages = List.of(
                new DocumentContent.PageContent(10, "附件：建筑机电安装工程专业承包一级证书复印件", List.of())
        );
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "h", "建筑机电安装工程专业承包一级", pages);

        List<String> requirements = List.of(
                "投标人须具备建筑机电安装工程专业承包二级及以上资质",
                "投标人须具备电力工程施工总承包一级资质"
        );

        List<QualificationDetail> details = QualificationMatchPolicy.buildDetails(requirements, certs, bidDoc, today);
        assertThat(details).hasSize(2);

        // 1. 机电安装：要求二级及以上，持有一级 -> MATCHED，提示优于/满足
        QualificationDetail d1 = details.get(0);
        assertThat(d1.status()).isEqualTo("MATCHED");
        assertThat(d1.matchedCertName()).isEqualTo("建筑机电安装工程专业承包一级");
        assertThat(d1.bidPageNumber()).isEqualTo(10);
        assertThat(d1.remark()).contains("优于招标要求【二级】");

        // 2. 电力工程：要求一级，持有三级 -> UNMATCHED，提示等级不足
        QualificationDetail d2 = details.get(1);
        assertThat(d2.status()).isEqualTo("UNMATCHED");
        assertThat(d2.remark()).contains("等级不足");
    }

    @Test
    @DisplayName("标书真实页码定位：自动剔除目录点线索引页，精确定位正文真实页码")
    void testBuildDetails_FiltersCatalogPage_LocatesActualContentPage() {
        LocalDate today = LocalDate.of(2026, 9, 6);
        List<Qualification> certs = List.of(
                Qualification.builder()
                        .id(46L)
                        .name("中华人民共和国增值电信业务经营许可证")
                        .expiryDate(LocalDate.of(2030, 3, 28))
                        .build()
        );

        List<DocumentContent.PageContent> pages = List.of(
                new DocumentContent.PageContent(2, "目录\n一、投标函..................... 3\n二、资质证书：增值电信业务经营许可证............ 120", List.of()),
                new DocumentContent.PageContent(120, "附件：中华人民共和国增值电信业务经营许可证（正本及副本复印件）", List.of())
        );
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "h", "目录 资质证书", pages);

        List<String> reqs = List.of("具备增值电信业务经营许可证");
        List<QualificationDetail> details = QualificationMatchPolicy.buildDetails(reqs, certs, bidDoc, today);

        assertThat(details).hasSize(1);
        QualificationDetail d = details.get(0);
        assertThat(d.status()).isEqualTo("MATCHED");
        // 关键断言：绝对定位在第 120 页正文，绝非第 2 页目录页！
        assertThat(d.bidPageNumber()).isEqualTo(120);
    }

    @Test
    @DisplayName("checkFirstStage：资质证书章节存在占位模板留白时直接 FAIL 硬拦截")
    void testCheckFirstStage_PlaceholderDetected_ReturnsFail() {
        List<DocumentContent.PageContent> pages = List.of(
                new DocumentContent.PageContent(55, "第四部分 企业资质证明\n安全生产许可证复印件：【此处粘贴安全生产许可证扫描件】", List.of())
        );
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "h", "企业资质证明", pages);
        TenderRequirements requirements = TenderRequirements.empty().withMergedQualificationConditions(List.of(
                new QualificationCondition("投标人须具有安全生产许可证", null, null)));

        Optional<QualityCheckItemResult> result = QualificationMatchPolicy.checkFirstStage(
                bidDoc, requirements);

        assertThat(result).isPresent();
        QualityCheckItemResult r = result.get();
        assertThat(r.itemCode()).isEqualTo(BidQualityCheckItem.QUALIFICATION_MATCH.getCode());
        assertThat(r.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(r.conclusion()).contains("占位模板或空白留白");
        assertThat(r.evidences()).isNotEmpty();
        assertThat(QualificationMatchPolicy.checkFirstStage(bidDoc, TenderRequirements.empty()).orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("checkFirstStage：资质章节真实完备无留白时返回 empty，放行移交 LLM lane")
    void testCheckFirstStage_CleanContent_ReturnsEmpty() {
        List<DocumentContent.PageContent> pages = List.of(
                new DocumentContent.PageContent(55, "第四部分 企业资质证明\n安全生产许可证编号：(沪)JZ安许证字[2023]001234，有效期至2028年5月", List.of())
        );
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "h", "企业资质证明", pages);

        Optional<QualityCheckItemResult> result = QualificationMatchPolicy.checkFirstStage(
                bidDoc, TenderRequirements.empty());

        assertThat(result).isEmpty();
    }
}
