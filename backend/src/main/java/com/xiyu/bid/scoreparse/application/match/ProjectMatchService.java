package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceRecordJpaRepository;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 项目业绩匹配（POST /api/knowledge/project/match，FR-010）。
 *
 * <p>规则：project_type/industry 命中 AND（signedAfter 空 OR signing_date ≥）
 * AND 金额条件。要求金额时，缺失金额不能作为合格证据。
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ProjectMatchService {

    private final PerformanceRecordJpaRepository repository;

    /** Stage-1 existence/count estimate; strict public matching constraints stay in match(). */
    public List<ProjectMatchedItem> findEstimateCandidates() {
        return repository.findAll().stream().map(row -> new ProjectMatchedItem(row.getId(), row.getContractName(),
                displayName(row), row.getGroupCompany(), row.getCustomerType() == null ? null : row.getCustomerType().displayName(),
                row.getSigningDate(), row.getContractAmount())).distinct().toList();
    }

    public KnowledgeMatchResult match(ProjectMatchRequest request) {
        if (request == null) {
            return KnowledgeMatchResult.empty("请求参数为空");
        }
        List<String> keywords = request.projectTypeKeywords() == null ? List.of() : request.projectTypeKeywords();
        List<ProjectType> enumHits = keywords.stream()
                .map(ProjectMatchService::parseProjectType)
                .filter(Objects::nonNull)
                .toList();
        List<CustomerType> customerTypeHits = keywords.stream()
                .map(ProjectMatchService::parseCustomerType)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> textKeywords = filterSpecificProjectKeywords(
                keywords.stream()
                        .filter(keyword -> parseProjectType(keyword) == null && parseCustomerType(keyword) == null)
                        .toList()
        );

        // 关键门禁：若没有任何可执行的业务过滤约束，严禁裸查全表导致假阳性满分！
        if (enumHits.isEmpty() && customerTypeHits.isEmpty() && textKeywords.isEmpty() && request.signedAfter() == null) {
            log.warn("业绩匹配缺乏有效约束（行业关键词/项目类型/客户类型/签约时间均未提供或被过滤），禁止按全量存在性满分: request={}", request);
            return KnowledgeMatchResult.empty("未抽取到可执行的业绩匹配约束（行业/类型/客户类型/签约时间），禁止全表匹配，待人工确认");
        }

        Specification<PerformanceRecordEntity> spec = specificationOf(enumHits, customerTypeHits, textKeywords, request.signedAfter());
        List<ProjectMatchedItem> matched = repository.findAll(spec).stream()
                .filter(row -> amountMatches(row.getContractAmount(), request.minContractAmount()))
                .map(row -> new ProjectMatchedItem(row.getId(), row.getContractName(),
                        displayName(row), row.getGroupCompany(),
                        row.getCustomerType() == null ? null : row.getCustomerType().displayName(),
                        row.getSigningDate(), row.getContractAmount()))
                .toList();
        MatchTierPolicy.Outcome outcome = MatchTierPolicy.evaluate(
                matched.size(), request.requiredCount(), false, false);
        return new KnowledgeMatchResult(outcome.tier(), outcome.matchRatio(), matched,
                matched.isEmpty() ? "未命中符合条件的业绩"
                        : "命中业绩 " + matched.size() + "/" + effectiveRequired(request.requiredCount()),
                matched.stream().map(row -> projectProof(row, request)).toList());
    }

    private static final java.util.Set<String> GENERIC_PROJECT_KEYWORDS = java.util.Set.of(
            "业绩", "供货", "类似", "采购", "服务", "数量", "评价", "排序", "规模",
            "合同", "案例", "时间", "要求", "项目", "情况",
            "总体", "注册", "协议", "单位", "提供", "评审", "应答人", "承诺",
            "基础", "条件", "资格条件", "交易服务", "交易", "全系统", "范围", "母公司", "子公司"
    );

    static List<String> filterSpecificProjectKeywords(List<String> rawKeywords) {
        if (rawKeywords == null || rawKeywords.isEmpty()) {
            return List.of();
        }
        return rawKeywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(k -> k.length() <= 8)
                .filter(k -> !GENERIC_PROJECT_KEYWORDS.contains(k))
                .filter(k -> !k.matches(".*(?:得|扣|满分)\\s*\\d+(?:\\.\\d+)?\\s*分.*")
                        && !k.matches(".*\\d+(?:\\.\\d+)?\\s*分(?!钟).*"))
                .filter(k -> !k.matches("(?i)^(优秀|良好|一般|合格|满分|分值|排序|评价|评审|评分|情况|要求|万元|亿元|以上|以下)$"))
                .toList();
    }

    private Specification<PerformanceRecordEntity> specificationOf(
            List<ProjectType> enumHits, List<CustomerType> customerTypeHits,
            List<String> textKeywords, LocalDate signedAfter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!customerTypeHits.isEmpty()) {
                predicates.add(root.get("customerType").in(customerTypeHits));
            }
            if (!enumHits.isEmpty()) {
                predicates.add(root.get("projectType").in(enumHits));
            } else if (!textKeywords.isEmpty()) {
                List<Predicate> contentPredicates = new ArrayList<>();
                textKeywords.stream()
                        .filter(StringUtils::hasText)
                        .forEach(keyword -> {
                            String pattern = "%" + keyword.trim() + "%";
                            contentPredicates.add(cb.or(
                                    cb.like(root.get("industry"), pattern),
                                    cb.like(root.get("contractName"), pattern),
                                    cb.like(root.get("groupCompany"), pattern)
                            ));
                        });
                predicates.add(cb.or(contentPredicates.toArray(new Predicate[0])));
            }
            if (signedAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("signingDate"), signedAfter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 明确要求金额时，缺失金额不可证明满足条件。 */
    private static boolean amountMatches(BigDecimal contractAmount, BigDecimal minContractAmount) {
        return minContractAmount == null
                || (contractAmount != null && contractAmount.compareTo(minContractAmount) >= 0);
    }

    private static KnowledgeMatchResult.RecordProof projectProof(ProjectMatchedItem row, ProjectMatchRequest request) {
        List<KnowledgeMatchResult.ConditionProof> conditions = new ArrayList<>();
        conditions.add(new KnowledgeMatchResult.ConditionProof("项目类型/行业",
                row.projectType() == null ? "MISSING_EVIDENCE" : "PASS",
                row.projectType() == null ? "业绩记录未提供项目类型或行业" : row.projectType()));
        if (request.signedAfter() != null) {
            conditions.add(new KnowledgeMatchResult.ConditionProof("签约日期不早于 " + request.signedAfter(),
                    row.signingDate() != null && !row.signingDate().isBefore(request.signedAfter()) ? "PASS" : "FAIL",
                    row.signingDate() == null ? "业绩记录未提供签约日期" : row.signingDate().toString()));
        }
        if (request.minContractAmount() != null) {
            conditions.add(new KnowledgeMatchResult.ConditionProof("合同金额不少于 " + request.minContractAmount(),
                    row.contractAmount() != null && row.contractAmount().compareTo(request.minContractAmount()) >= 0
                            ? "PASS" : "FAIL",
                    row.contractAmount() == null ? "业绩记录未提供合同金额" : row.contractAmount().toPlainString()));
        }
        conditions.add(new KnowledgeMatchResult.ConditionProof("客户",
                row.customerName() == null && row.customerType() == null ? "MISSING_EVIDENCE" : "PASS",
                row.customerName() != null ? row.customerName()
                        : row.customerType() != null ? row.customerType() : "业绩记录未提供客户信息"));
        return new KnowledgeMatchResult.RecordProof("PROJECT", String.valueOf(row.id()), row.contractName(), conditions);
    }

    private static final java.util.Set<String> CENTRAL_SOE_TERMS = java.util.Set.of("央企", "中央企业", "中央金融企业");
    private static final java.util.Set<String> LOCAL_SOE_TERMS = java.util.Set.of("地方国企", "国企", "国有企业");
    private static final java.util.Set<String> GOV_TERMS = java.util.Set.of("政府机关", "政府", "事业单位", "高校");
    private static final java.util.Set<String> PRIVATE_TERMS = java.util.Set.of("民企", "民营企业", "民营");

    static CustomerType parseCustomerType(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > 10) {
            return null;
        }
        for (CustomerType type : CustomerType.values()) {
            if (type.name().equalsIgnoreCase(trimmed) || type.displayName().equals(trimmed)) {
                return type;
            }
        }
        if (CENTRAL_SOE_TERMS.contains(trimmed)) {
            return CustomerType.CENTRAL_SOE;
        }
        if (LOCAL_SOE_TERMS.contains(trimmed)) {
            return CustomerType.LOCAL_SOE;
        }
        if (GOV_TERMS.contains(trimmed)) {
            return CustomerType.GOVERNMENT_INSTITUTION;
        }
        if (PRIVATE_TERMS.contains(trimmed)) {
            return CustomerType.PRIVATE_ENTERPRISE;
        }
        return null;
    }

    static ProjectType parseProjectType(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        if ("办公用品".equals(trimmed) || "办公类".equals(trimmed)) {
            return ProjectType.OFFICE;
        }
        if ("MRO".equalsIgnoreCase(trimmed)) {
            return ProjectType.INDUSTRIAL;
        }
        for (ProjectType type : ProjectType.values()) {
            if (type.name().equalsIgnoreCase(trimmed) || type.displayName().equals(trimmed)) {
                return type;
            }
        }
        return null;
    }

    private static String displayName(PerformanceRecordEntity row) {
        return row.getProjectType() != null ? row.getProjectType().displayName() : row.getIndustry();
    }

    private static int effectiveRequired(Integer requiredCount) {
        return (requiredCount == null || requiredCount <= 0) ? 1 : requiredCount;
    }
}
