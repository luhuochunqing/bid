package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CaseRecommendationPurposePolicyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "商品丰富性 | 自营商品SKU数量由多到少排序。须提供本期系统截图。 | MANUAL_EVIDENCE",
            "使用规模 | 按活跃用户数量评分，提供平台统计报表。 | MANUAL_EVIDENCE",
            "平台注册用户数 | 按用户数量排序，第一名得4分。提交带时间戳的后台截图。 | MANUAL_EVIDENCE",
            "平台注册用户数 | 第一名得4分。提交带时间戳的后台截图。 | MANUAL_EVIDENCE",
            "线上平台运营 | 平台成交金额不低于５００万元，提供对应的对账报表。 | MANUAL_EVIDENCE",
            "实际响应时长 | 响应时长不超过2小时得3分，上传近三月服务台账。 | MANUAL_EVIDENCE",
            "仓储能力 | 仓储总面积不小于15000平方米，提供仓库产权证明材料。 | MANUAL_EVIDENCE",
            "仓储能力 | 提供仓库或服务网点证明材料，5-10个得1分。 | MANUAL_EVIDENCE",
            "仓储能力 | 提供仓库或服务网点证明材料，低于5个得0.5分。 | MANUAL_EVIDENCE",
            "履约方案 | 编制配送方案；自营网点数量由多到少排名，提供盖章证明材料。 | REFERENCE_AND_MANUAL_EVIDENCE",
            "配送能力 | 提供物流配送方案，说明2小时响应能力，经评委综合评价。 | REFERENCE",
            "技术方案 | 提供平台操作方案及示例截图，由评委按方案合理性分档打分。 | REFERENCE",
            "商品质量管控 | 提供商品质量管理方案及相关材料，评委分档评分。 | REFERENCE",
            "平台运营年限 | 提供ICP备案截图；运营时长3年以上得5分，1-3年得2分，1年以下不得分。 | MANUAL_EVIDENCE",
            "平台运营年限 | ICP备案截图，运营时长3年以上得5分/1-3年得2分。 | MANUAL_EVIDENCE",
            "系统对接能力 | 每提供1个对接案例得0.5分，最高得5分（需提供接口截图或日志证明材料）。 | MANUAL_EVIDENCE",
            "等保备案证明 | 具有第三级及以上等级保护测评报告或备案证明得2分，需提供证明材料扫描件。 | MANUAL_EVIDENCE"
    })
    void separatesRequiredProofFromHumanEvaluatedReference(
            String title, String detail, CaseRecommendationPurposePolicy.Purpose expected) {
        assertThat(CaseRecommendationPurposePolicy.determineRecommendationPurpose(title, detail)).isEqualTo(expected);
    }
}
