package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.infrastructure.dto.CrmOpportunityDto;
import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CRM 商机查询接口。
 * <p>供标讯详情页 CRM 商机关联使用。当前为 Mock 实现，真实 CRM API 就绪后替换。
 */
@RestController
@RequestMapping("/api/crm")
@PreAuthorize("isAuthenticated()")
public class CrmOpportunityController {

    /**
     * 根据招标主体+报名截止时间+开标时间精确搜索商机列表。
     * Mock: 返回 2-3 条匹配的虚拟商机。
     */
    @PostMapping("/opportunities/search")
    public ResponseEntity<ApiResponse<List<CrmOpportunityDto>>> searchOpportunities(
            @RequestBody Map<String, String> request) {

        // TODO: 替换为真实 CRM API 调用
        // 真实实现：POST /crm/opportunity/search 或类似接口
        // 入参: tenderer/purchaserName + registrationDeadline + bidOpeningTime 精准匹配
        // 返回格式: [{id: "OPP001", title: "XX公司采购项目"}]

        String tenderer = request != null ? request.get("tenderer") : null;
        if (tenderer == null || tenderer.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success("查询成功", Collections.emptyList()));
        }

        // Stub: 当有招标主体时返回 mock 数据
        List<CrmOpportunityDto> mockOpportunities = List.of(
            new CrmOpportunityDto("OPP-2026-001", tenderer + "-年度MRO采购项目", tenderer),
            new CrmOpportunityDto("OPP-2026-002", tenderer + "-办公用品框架协议", tenderer),
            new CrmOpportunityDto("OPP-2026-003", tenderer + "-设备维保服务采购", tenderer)
        );
        return ResponseEntity.ok(ApiResponse.success("查询成功", mockOpportunities));
    }

    /**
     * 获取商机的评估表三段数据。
     * Mock: 返回虚拟评估数据。
     */
    @GetMapping("/opportunities/{opportunityId}/evaluation-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEvaluationData(
            @PathVariable String opportunityId) {

        Map<String, Object> mockData = Map.of(
            "opportunityId", opportunityId,
            "basic", Map.of(
                "projectBackground", "本项目为年度MRO物资框架采购，涉及工具、劳保、办公耗材等品类，预计年采购金额500万元。",
                "competitorAnalysis", "主要竞争对手为西域MRO、震坤行、工品汇等头部MRO平台，竞争较为激烈。",
                "contractPeriodStart", LocalDate.now().plusDays(30).toString(),
                "contractPeriodEnd", LocalDate.now().plusDays(30).plusYears(1).toString(),
                "shortlistedCount", 5,
                "platformServiceFee", new BigDecimal("80000")
            ),
            "customerInfos", List.of(
                Map.ofEntries(
                    Map.entry("roleKey", "DECISION_MAKER"),
                    Map.entry("NAME", "张德明"), Map.entry("POSITION", "采购总监"),
                    Map.entry("XIYU_CONTACT", "13800138001"), Map.entry("CONTACT_METHOD", "电话"),
                    Map.entry("EVALUATION_BASIS", "综合评分法"), Map.entry("CONTACTED", "Y"),
                    Map.entry("HIGH_LEVEL_EXCHANGE", "Y"), Map.entry("GUIDED_BID", "Y"),
                    Map.entry("CAN_GET_KEY_INFO", "Y"), Map.entry("CAN_REMOVE_ADVERSE", "N"),
                    Map.entry("KEY_TARGET", "Y"), Map.entry("CAN_SYNC_EVAL", "Y"), Map.entry("TENDENCY", "中性")
                ),
                Map.ofEntries(
                    Map.entry("roleKey", "INFLUENCER"),
                    Map.entry("NAME", "李明华"), Map.entry("POSITION", "技术部经理"),
                    Map.entry("XIYU_CONTACT", "13800138002"), Map.entry("CONTACT_METHOD", "微信"),
                    Map.entry("EVALUATION_BASIS", "技术方案"), Map.entry("CONTACTED", "Y"),
                    Map.entry("HIGH_LEVEL_EXCHANGE", "N"), Map.entry("GUIDED_BID", "N"),
                    Map.entry("CAN_GET_KEY_INFO", "N"), Map.entry("CAN_REMOVE_ADVERSE", "N"),
                    Map.entry("KEY_TARGET", "N"), Map.entry("CAN_SYNC_EVAL", "Y"), Map.entry("TENDENCY", "支持")
                ),
                Map.ofEntries(
                    Map.entry("roleKey", "EVALUATOR"),
                    Map.entry("NAME", "王建国"), Map.entry("POSITION", "评标专家"),
                    Map.entry("XIYU_CONTACT", "13800138003"), Map.entry("CONTACT_METHOD", "邮件"),
                    Map.entry("EVALUATION_BASIS", "价格与资质"), Map.entry("CONTACTED", "N"),
                    Map.entry("HIGH_LEVEL_EXCHANGE", "N"), Map.entry("GUIDED_BID", "N"),
                    Map.entry("CAN_GET_KEY_INFO", "N"), Map.entry("CAN_REMOVE_ADVERSE", "N"),
                    Map.entry("KEY_TARGET", "N"), Map.entry("CAN_SYNC_EVAL", "Y"), Map.entry("TENDENCY", "待沟通")
                )
            ),
            "recommendation", Map.of(
                "shouldBid", true,
                "reason", "该项目与公司主营业务高度匹配，预算充足，客户关系良好，建议重点跟进投标。"
            )
        );

        return ResponseEntity.ok(ApiResponse.success("查询成功", mockData));
    }
}
