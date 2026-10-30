package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseQualificationMatchPolicyTest {

    private final LocalDate today = LocalDate.of(2026, 10, 30);

    @Test
    @DisplayName("精准匹配：招商积余标书ISO三体系条款能够准确匹配在库的三张ISO证书与扫描件")
    void testMatchIsoStandardsForZhaoshangJiyu() {
        String dim = "1 管理体系";
        String detail = "1、评审标准：1）具有 ISO9001 质量管理体系认证、ISO14001环境管理体系认证、ISO45001职业健康安全管理体系认证的得10分。2）三项齐全得满分，缺少一项，不得分3）不满足或未提供证明文件者，不得分2、证明文件：投标人须提供在有效期的认证证书复印件或扫描件加盖投标人公章。";

        List<EnterpriseQualificationMatchPolicy.Candidate> candidates = List.of(
                new EnterpriseQualificationMatchPolicy.Candidate(
                        1L, "质量管理体系认证证书(ISO9001:2015)", "三级", "00121Q34567R0M", "中国质量认证中心",
                        LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1), "VALID",
                        "低压成套开关设备及控制设备制造与销售", "https://oss.ehsy.com/cert/iso9001.jpg", "iso9001.jpg", false
                ),
                new EnterpriseQualificationMatchPolicy.Candidate(
                        2L, "环境管理体系认证证书(ISO14001:2015)", "二级", "00121E34567R0M", "中国质量认证中心",
                        LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1), "VALID",
                        "工业品仓储与物流服务", "https://oss.ehsy.com/cert/iso14001.jpg", "iso14001.jpg", false
                ),
                new EnterpriseQualificationMatchPolicy.Candidate(
                        3L, "职业健康安全管理体系认证(ISO45001:2018)", "一级", "00121S34567R0M", "中国质量认证中心",
                        LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1), "VALID",
                        "工业品销售与售后服务", "https://oss.ehsy.com/cert/iso45001.jpg", "iso45001.jpg", false
                ),
                new EnterpriseQualificationMatchPolicy.Candidate(
                        4L, "建筑机电安装工程专业承包壹级", "一级", "D123456789", "住建部",
                        LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "VALID",
                        "机电安装工程施工", "https://oss.ehsy.com/cert/const.jpg", "const.jpg", false
                )
        );

        List<EnterpriseQualificationMatchPolicy.MatchResult> results =
                EnterpriseQualificationMatchPolicy.match(dim, detail, null, candidates, today);

        // 仅三张 ISO 证书命中，建筑施工不命中
        assertThat(results).hasSize(3);
        assertThat(results).extracting(EnterpriseQualificationMatchPolicy.MatchResult::id)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(results).allMatch(r -> r.matchScore() >= 90);
        assertThat(results).allMatch(r -> r.fileUrl() != null && r.fileUrl().endsWith(".jpg"));
        assertThat(results.get(0).matchReasons()).anyMatch(re -> re.contains("认证要求"));
    }

    @Test
    @DisplayName("过期证书识别与降级：过期证书予以标记并不作为高分推荐")
    void testExpiredCertificateIsFlaggedAndPenalized() {
        String dim = "技术部分";
        String detail = "投标人需具备ISO9001质量管理体系认证";

        List<EnterpriseQualificationMatchPolicy.Candidate> candidates = List.of(
                new EnterpriseQualificationMatchPolicy.Candidate(
                        10L, "ISO9001质量管理体系证书", "AAA", "001Q1", "认证机构",
                        LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1), "VALID",
                        "销售", "https://oss.ehsy.com/cert/expired.jpg", "expired.jpg", false
                )
        );

        List<EnterpriseQualificationMatchPolicy.MatchResult> results =
                EnterpriseQualificationMatchPolicy.match(dim, detail, null, candidates, today);

        // 2026-01-01 已经早于 today(2026-10-30)，已过期
        assertThat(results).hasSize(1);
        EnterpriseQualificationMatchPolicy.MatchResult res = results.get(0);
        assertThat(res.status()).isEqualTo("EXPIRED");
        assertThat(res.matchScore()).isLessThan(50);
        assertThat(res.matchReasons()).anyMatch(r -> r.contains("已于 2026-01-01 过期"));
    }

    @Test
    @DisplayName("已下架/已报废证书自动过滤")
    void testRetiredCertificatesAreExcluded() {
        String dim = "资质条件";
        String detail = "要求具有营业执照";

        List<EnterpriseQualificationMatchPolicy.Candidate> candidates = List.of(
                new EnterpriseQualificationMatchPolicy.Candidate(
                        20L, "企业法人营业执照", null, "91310000X", "市监局",
                        LocalDate.of(2015, 1, 1), null, "RETIRED",
                        "综合零售", null, null, true
                )
        );

        List<EnterpriseQualificationMatchPolicy.MatchResult> results =
                EnterpriseQualificationMatchPolicy.match(dim, detail, null, candidates, today);

        assertThat(results).isEmpty();
    }
}
