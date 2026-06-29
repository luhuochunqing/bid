package com.xiyu.bid.ai.client;

final class AiPromptTemplates {

    static final String BID_PREVIEW_SYSTEM_INSTRUCTION =
            "请对投标文件做总体质量评估，给出总体评价和3-5个主要风险点。";

    static final String BID_PREVIEW_OUTPUT_FORMAT =
            "返回JSON格式：overallAssessment 和 keyRisks 数组";

    private AiPromptTemplates() {
    }
}
