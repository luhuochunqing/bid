package com.xiyu.bid.biddraftagent.domain.risk;

public enum RiskLevel {
    RED_LINE,  // 废标红线 — 直接导致废标的条款
    WARNING,   // 一般风险 — 需关注但不直接废标
    INFO       // 信息提示
}
