// Input: 各种招标资质描述与企业资质证书
// Output: 验证 QualificationMatchPolicy 智能匹配、双向包含、特征词命中、明细比对生成
// Pos: compliance/domain/policy/ - 资质匹配纯核心单元测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.entity.Qualification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationMatchPolicyTest {

    @Test
    @DisplayName("双向匹配：大模型提取长句 requirement 成功匹配短 certName")
    void testMatch_LongRequirementMatchesShortCertName() {
        String req = "投标人须具备有效的质量管理体系认证（ISO9001）证书且在有效期内";
        String cert = "ISO9001质量管理体系认证证书";

        boolean matched = QualificationMatchPolicy.isQualificationMatch(req, cert);
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("双向匹配：短 requirement（测试或关键词）成功匹配长 certName")
    void testMatch_ShortKeywordMatchesLongCertName() {
        String req = "ISO27001";
        String cert = "ISO27001信息安全管理体系认证（甲级）";

        boolean matched = QualificationMatchPolicy.isQualificationMatch(req, cert);
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("标点与全半角差异不阻碍匹配")
    void testMatch_PunctuationAndSpacingDifference() {
        String req = "具备 ISO-27001 / CMMI 3级 认证";
        String cert = "CMMI3级软件能力成熟度模型集成";

        boolean matched = QualificationMatchPolicy.isQualificationMatch(req, cert);
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("行业专业资质匹配：建筑机电安装工程专业承包")
    void testMatch_EngineeringQualification() {
        String req = "投标人须具备建筑机电安装工程专业承包一级及以上资质";
        String cert = "建筑机电安装工程专业承包一级";

        boolean matched = QualificationMatchPolicy.isQualificationMatch(req, cert);
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("完全不相关的资质不匹配")
    void testMatch_UnrelatedQualifications_ReturnsFalse() {
        String req = "投标人须具备承装（修、试）电力设施许可证";
        String cert = "ISO9001质量管理体系认证";

        boolean matched = QualificationMatchPolicy.isQualificationMatch(req, cert);
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("buildDetails：生成完整三方比对清单（招标要求 vs 我司证书 vs 标书页码）")
    void testBuildDetails_GeneratesStructuredComparison() {
        LocalDate today = LocalDate.of(2026, 9, 3);
        List<Qualification> certs = List.of(
                Qualification.builder()
                        .id(1L)
                        .name("ISO9001质量管理体系认证")
                        .expiryDate(LocalDate.of(2027, 5, 1))
                        .build(),
                Qualification.builder()
                        .id(2L)
                        .name("安全生产许可证")
                        .expiryDate(LocalDate.of(2026, 9, 20)) // 剩余 17 天，即将到期
                        .build()
        );

        List<DocumentContent.PageContent> pages = List.of(
                new DocumentContent.PageContent(1, "投标文件 目录 封面", List.of()),
                new DocumentContent.PageContent(28, "附件四：ISO9001质量管理体系认证证书复印件，证书编号：CNCA-123456", List.of())
        );
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash", "投标文件 目录 封面 附件四：ISO9001质量管理体系认证证书复印件", pages);

        List<String> requirements = List.of(
                "投标人须具备有效的质量管理体系认证（ISO9001）",
                "具有有效的安全生产许可证",
                "具有电力工程施工总承包特级资质"
        );

        List<QualificationDetail> details = QualificationMatchPolicy.buildDetails(requirements, certs, bidDoc, today);

        assertThat(details).hasSize(3);

        // 第 1 项：ISO9001，已满足，标书第 28 页
        QualificationDetail d1 = details.get(0);
        assertThat(d1.requirement()).isEqualTo("投标人须具备有效的质量管理体系认证（ISO9001）");
        assertThat(d1.matchedCertName()).isEqualTo("ISO9001质量管理体系认证");
        assertThat(d1.status()).isEqualTo("MATCHED");
        assertThat(d1.bidPageNumber()).isEqualTo(28);
        assertThat(d1.remainingDays()).isGreaterThan(60);

        // 第 2 项：安全生产许可证，即将到期（需关注）
        QualificationDetail d2 = details.get(1);
        assertThat(d2.matchedCertName()).isEqualTo("安全生产许可证");
        assertThat(d2.status()).isEqualTo("ATTENTION");
        assertThat(d2.remainingDays()).isEqualTo(17);

        // 第 3 项：电力工程，缺失未匹配
        QualificationDetail d3 = details.get(2);
        assertThat(d3.status()).isEqualTo("UNMATCHED");
        assertThat(d3.matchedCertName()).isEmpty();
    }

    @Test
    @DisplayName("事故 1358 真实用例：招标长句提取成功匹配国字头全称中华人民共和国增值电信业务经营许可证")
    void testMatch_Incident1358_TelecomBusinessLicenseMatchesGovPrefixCert() {
        String req = "增值电信业务经营许可证，业务种类及覆盖范围需包括信息服务业务（互联网信息服务）及在线数据处理与交易业务（经营类电子商务）";
        String cert = "中华人民共和国增值电信业务经营许可证";

        boolean matched = QualificationMatchPolicy.isQualificationMatch(req, cert);
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("事故 1358 buildDetails：增值电信许可证精准匹配、消除未维护误报并正确定位标书第 167 页")
    void testBuildDetails_Incident1358_MatchesTelecomLicenseAndLocatesPage167() {
        LocalDate today = LocalDate.of(2026, 9, 4);
        List<Qualification> certs = List.of(
                Qualification.builder()
                        .id(46L)
                        .name("中华人民共和国增值电信业务经营许可证")
                        .expiryDate(LocalDate.of(2030, 3, 28))
                        .build()
        );

        List<DocumentContent.PageContent> pages = List.of(
                new DocumentContent.PageContent(1, "投标文件 封面", List.of()),
                new DocumentContent.PageContent(167, "证明材料 附件三：增值电信业务经营许可证（互联网信息服务与在线数据处理）复印件", List.of())
        );
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash", "增值电信业务经营许可证", pages);

        List<String> requirements = List.of(
                "增值电信业务经营许可证，业务种类及覆盖范围需包括信息服务业务（互联网信息服务）及在线数据处理与交易业务（经营类电子商务）"
        );

        List<QualificationDetail> details = QualificationMatchPolicy.buildDetails(requirements, certs, bidDoc, today);

        assertThat(details).hasSize(1);
        QualificationDetail d = details.get(0);
        assertThat(d.status()).isEqualTo("MATCHED");
        assertThat(d.matchedCertName()).isEqualTo("中华人民共和国增值电信业务经营许可证");
        assertThat(d.bidPageNumber()).isEqualTo(167);
        assertThat(d.remainingDays()).isGreaterThan(1000);
        assertThat(d.remark()).contains("已匹配企业资质库有效证书「中华人民共和国增值电信业务经营许可证」");
        assertThat(d.remark()).doesNotContain("未维护");
    }
}
