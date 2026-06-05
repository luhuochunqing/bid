// Input: extracted tender document text in e2e profile
// Output: deterministic TenderRequirementProfile for parse-to-task E2E coverage
// Pos: biddraftagent/infrastructure/e2e - test-profile adapter, no production activation
package com.xiyu.bid.biddraftagent.infrastructure.e2e;

import com.xiyu.bid.biddraftagent.application.TenderDocumentAnalysisInput;
import com.xiyu.bid.biddraftagent.application.TenderDocumentAnalyzer;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Primary
@Profile("e2e")
public class E2eTenderDocumentAnalyzer implements TenderDocumentAnalyzer {

    @Override
    public TenderRequirementProfile analyze(TenderDocumentAnalysisInput input) {
        return new TenderRequirementProfile(
                "西域 MRO 数字化投标测试项目",
                "西域 MRO 数字化投标测试项目招标文件",
                "数字化投标管理平台建设、接口对接与上线实施服务",
                "西域测试采购中心",
                null,
                "上海",
                "数字化采购",
                null,
                LocalDateTime.of(2026, 5, 30, 10, 0),
                List.of("提供营业执照、法人授权书、类似项目经验证明"),
                List.of("提交平台实施方案、接口对接方案和项目进度计划"),
                List.of("提交商务条款响应、报价说明和售后服务承诺"),
                List.of("技术方案完整性、商务响应完整性、资料真实性为主要评分点"),
                List.of(),
                "投标截止时间以项目计划为准",
                List.of("营业执照", "法人授权书", "项目经验证明"),
                List.of("需复核商务偏离与技术响应覆盖完整性"),
                List.of("e2e", "tender-breakdown"),
                List.of(
                        new TenderRequirementItemSnapshot(
                                "commercial",
                                "商务条款响应",
                                "准备商务条款响应、报价说明和售后服务承诺",
                                true,
                                "商务条款要求",
                                95,
                                "商务条款"
                        ),
                        new TenderRequirementItemSnapshot(
                                "technical",
                                "平台实施方案",
                                "准备平台实施方案、接口对接方案和项目进度计划",
                                true,
                                "技术方案要求",
                                95,
                                "技术方案"
                        ),
                        new TenderRequirementItemSnapshot(
                                "qualification",
                                "企业资质材料",
                                "准备营业执照、法人授权书、类似项目经验证明",
                                true,
                                "资格材料要求",
                                94,
                                "资格材料"
                        )
                )
        );
    }
}
