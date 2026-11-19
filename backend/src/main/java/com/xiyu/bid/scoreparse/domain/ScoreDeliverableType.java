package com.xiyu.bid.scoreparse.domain;

/**
 * 评分项交付物/证明材料形态。
 * <p>上游 ScoreParse LLM 抽取并归一化；供下游 Casework 智能推荐等模块进行确定性类型路由。
 */
public enum ScoreDeliverableType {
    /** 纯文本方案论述（技术方案、服务方案、实施方案、售后服务、培训承诺、管理制度等） */
    TEXT_PROPOSAL,
    /** 系统截图/界面演示（软件操作截图、电商平台功能、自营商品管理界面、系统对接演示等） */
    SYSTEM_SCREENSHOT,
    /** 资质证书/证照（ISO认证、资质证书、厂商授权书、资信等级等） */
    QUALIFICATION_CERT,
    /** 业绩合同/发票（过往同类项目合同、结算发票、中标通知书、用户评价证明等） */
    CONTRACT_INVOICE,
    /** 其他证明材料或无法归类形态（审计报告、承诺函、社保证明等） */
    OTHER;

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ScoreDeliverableType.valueOf(value.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            return OTHER.name();
        }
    }
}
