// Input: 无（静态默认词汇表）
// Output: ScoreVocabularyRegistry Bean（全局评分维度词汇 escape-hatch）
// Pos: scoreparse/infrastructure/bootstrap — Phase 5 词汇治理注册表装配
// 维护声明: 新增全局词汇条目在此追加；项目特定条目由调用方通过 addProject 动态注册
package com.xiyu.bid.scoreparse.infrastructure.bootstrap;

import com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 评分词汇注册表默认装配（Phase 5 escape-hatch）。
 * <p>全局条目覆盖招标文件高频评分维度，供 LLM 归并 prompt 注入词汇参考，
 * 防止通用 prompt 对行业特定术语的误判或遗漏。
 * <p>项目特定条目（如某项目独有的"速动比率""营业收现比率"）由业务层
 * 通过 {@link ScoreVocabularyRegistry.Builder#addProject} 动态注册后重建 Bean。
 */
@Configuration
public class ScoreVocabularyConfiguration {

    @Bean
    public ScoreVocabularyRegistry scoreVocabularyRegistry() {
        return ScoreVocabularyRegistry.builder()
                .addGlobal("财务状况", Set.of("财务评审", "财务指标"), Set.of("速动比率", "营业收现比率", "资产负债率", "净利润", "营业收入", "现金流"))
                .addGlobal("信用评价", Set.of("信用记录", "信誉评审"), Set.of("不良记录", "处罚记录", "信用评级"))
                .addGlobal("供应商业绩", Set.of("业绩评审", "类似项目业绩"), Set.of("合同业绩", "中标通知书", "验收报告"))
                .addGlobal("价格折扣得分", Set.of("报价得分", "价格分"), Set.of("评标基准价", "报价折扣率", "线性插入法"), 30.0, 60.0)
                .addGlobal("技术方案", Set.of("技术评分", "技术部分"), Set.of("方案完整性", "技术路线", "实施方案"))
                .addGlobal("服务能力", Set.of("售后服务", "服务保障"), Set.of("服务网点", "响应时间", "服务承诺"))
                .addGlobal("团队资质", Set.of("人员资质", "项目团队"), Set.of("项目经理", "高级职称", "执业资格"))
                .addGlobal("体系认证", Set.of("管理体系", "认证证书"), Set.of("ISO9001", "ISO14001", "OHSAS18001"))
                .build();
    }
}
