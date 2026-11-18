package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScoreItemIntentRouter: 评分项意图确定性路由策略测试")
class ScoreItemIntentRouterTest {

    @ParameterizedTest(name = "资质证书项: {0}")
    @CsvSource(delimiter = '|', value = {
            "5 质量管理体系认证（ISO9001） | 提供有效的ISO9001质量管理体系认证证书复印件",
            "企业资质与认证 | 须具备信息安全管理体系认证证书ISO27001",
            "资信等级 | 具有AAA级企业信用等级证书",
            "CMMI能力认证 | 具备CMMI3级及以上软件成熟度资质证书",
            "特种许可 | 具备有效的危险化学品经营许可证",
            "技术部分 | 投标人须具备省级高新技术企业资质证书扫描件" // 泛章节名穿透细则
    })
    void shouldRouteToQualification(String dim, String detail) {
        ScoreItemIntentRouter.Intent intent = ScoreItemIntentRouter.route(dim, detail);
        assertThat(intent).isEqualTo(ScoreItemIntentRouter.Intent.QUALIFICATION);
        assertThat(intent.isQualification()).isTrue();
    }

    @ParameterizedTest(name = "同类业绩项: {0}")
    @CsvSource(delimiter = '|', value = {
            "3 同类项目业绩 | 近三年内类似办公品供货项目合同业绩",
            "供货业绩证明 | 单笔合同金额不低于2000万元的类似项目供货业绩，附发票",
            "详细评审 | 具有央企同类电商平台对接业绩合同", // 泛章节名穿透
            "资质业绩 | 类似项目合同及用户评价" // 混合标签按业绩优先
    })
    void shouldRouteToPerformance(String dim, String detail) {
        ScoreItemIntentRouter.Intent intent = ScoreItemIntentRouter.route(dim, detail);
        assertThat(intent).isEqualTo(ScoreItemIntentRouter.Intent.PERFORMANCE);
        assertThat(intent.isPerformance()).isTrue();
    }

    @ParameterizedTest(name = "人员团队项: {0}")
    @CsvSource(delimiter = '|', value = {
            "项目团队与人员配置 | 项目经理需具备一级建造师证书，团队配备不少于5人",
            "技术负责人资质 | 具备信息系统项目管理师证书，提供近半年社保证明",
            "综合评审 | 项目实施团队人员配置合理，项目经理具有同类项目实施经验"
    })
    void shouldRouteToPersonnel(String dim, String detail) {
        ScoreItemIntentRouter.Intent intent = ScoreItemIntentRouter.route(dim, detail);
        assertThat(intent).isEqualTo(ScoreItemIntentRouter.Intent.PERSONNEL);
        assertThat(intent.isPersonnel()).isTrue();
    }

    @ParameterizedTest(name = "商务财务项: {0}")
    @CsvSource(delimiter = '|', value = {
            "商务条款响应 | 账期60天，付款方式无偏离，提交投标保函",
            "财务状况审计 | 经会计师事务所审计的近三年财务审计报告，资产负债率合理",
            "价格与报价明细 | 报价一览表完整，折扣率明确"
    })
    void shouldRouteToCommercial(String dim, String detail) {
        ScoreItemIntentRouter.Intent intent = ScoreItemIntentRouter.route(dim, detail);
        assertThat(intent).isEqualTo(ScoreItemIntentRouter.Intent.COMMERCIAL);
        assertThat(intent.isCommercial()).isTrue();
    }

    @ParameterizedTest(name = "技术方案项: {0}")
    @CsvSource(delimiter = '|', value = {
            "技术架构与方案 | 采用微服务分布式架构，支持高并发容灾部署",
            "45 配送能力评价 | 具备完善的物流配送体系，提供自营仓储与最后一公里配送能力",
            "售后服务与SLA保障 | 7x24小时快速响应机制，故障恢复时间小于2小时",
            "应急预案与突发保障 | 防汛防台应急保障措施与突发事件处置组织架构",
            "系统接口对接方案 | 具备与采购方ERP系统打通的API对接经验与联调能力"
    })
    void shouldRouteToTechnical(String dim, String detail) {
        ScoreItemIntentRouter.Intent intent = ScoreItemIntentRouter.route(dim, detail);
        assertThat(intent).isEqualTo(ScoreItemIntentRouter.Intent.TECHNICAL);
        assertThat(intent.isTechnical()).isTrue();
    }

    @Test
    void shouldHandleNullAndEmptyGracefully() {
        assertThat(ScoreItemIntentRouter.route(null, null)).isEqualTo(ScoreItemIntentRouter.Intent.OTHER);
        assertThat(ScoreItemIntentRouter.route("", "")).isEqualTo(ScoreItemIntentRouter.Intent.OTHER);
        assertThat(ScoreItemIntentRouter.route("未知章节", "无特殊要求描述")).isEqualTo(ScoreItemIntentRouter.Intent.OTHER);
    }
}
