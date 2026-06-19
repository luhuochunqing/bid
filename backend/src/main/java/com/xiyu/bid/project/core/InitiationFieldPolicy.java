// Input: InitiationInput (PRD §3.1.1 字段集合) + 锁定字段差异
// Output: Decision (Allow | Deny{missingFields, reasons}) + lockedFields()
// Pos: project/core/ - pure rule, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * PRD §3.1 立项字段策略（纯规则，无 Spring/JPA）。
 *
 * <p>必填项（§3.1.1）：业主单位、入围家数、合同期限(start+end)、
 * 客户类型、营业收入、开标时间、业务负责人、归属部门、保证金额、缴纳方式。
 * 项目类型改为可选项（CO-281）。
 * 投标月份由 bidOpenTime 推导，不直接校验；竞争对手为可选项。
 *
 * <p>锁定项（§3.1.2，提交后不可改）：bidOpenTime、ownerUnit。
 */
public final class InitiationFieldPolicy {

    /** §3.1.1 项目类型枚举（办公/综合/集采/工业品/其他），对齐前端 PROJECT_TYPE_OPTIONS。 */
    public enum ProjectType { OFFICE, COMPREHENSIVE, COLLECTIVE, INDUSTRIAL, OTHER }

    /** §3.1.1 客户类型枚举（政府/央企/地方国企/民企/港澳台及外企），对齐前端 CUSTOMER_TYPE_OPTIONS。 */
    public enum CustomerType { GOVERNMENT, CENTRAL_SOE, LOCAL_SOE, PRIVATE, FOREIGN }

    private static final Set<String> LOCKED_FIELDS = Set.of("bidOpenTime", "ownerUnit");

    private InitiationFieldPolicy() {
    }

    /** 提交后不可变更的字段集合（§3.1.2）。 */
    public static Set<String> lockedFields() {
        return LOCKED_FIELDS;
    }

    /**
     * 校验提交输入的必填项。
     */
    public static Decision validate(InitiationInput input) {
        Objects.requireNonNull(input, "input 不能为空");
        List<String> missing = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        requireText("ownerUnit", input.ownerUnit(), missing);
        requirePositive("expectedBidders", input.expectedBidders(), missing);
        requireNotNull("customerType", input.customerType(), missing);
        requirePositiveAmount("annualRevenue", input.annualRevenue(), missing);
        requireNotNull("bidOpenTime", input.bidOpenTime(), missing);
        requirePositive("ownerUserId", input.ownerUserId(), missing);
        requireText("departmentSnapshot", input.departmentSnapshot(), missing);
        // 蓝图 §3.3.1.1: 保证金逻辑 — 选择 YES 时金额+方式必填
        if ("YES".equals(input.needDeposit())) {
            requirePositiveAmount("depositAmount", input.depositAmount(), missing);
            requireText("depositPaymentMethod", input.depositPaymentMethod(), missing);
        }

        return missing.isEmpty()
                ? Decision.ALLOW
                : new Decision.Deny(Collections.unmodifiableList(missing),
                                    Collections.unmodifiableList(reasons));
    }

    /**
     * 校验更新差异：检测是否触碰了锁定字段。lockedAlready=true 时锁定字段差异即拒绝。
     */
    public static Decision validateUpdate(InitiationInput existing, InitiationInput requested, boolean lockedAlready) {
        Objects.requireNonNull(existing, "existing 不能为空");
        Objects.requireNonNull(requested, "requested 不能为空");
        if (!lockedAlready) {
            return Decision.ALLOW;
        }
        List<String> violations = new ArrayList<>();
        if (!Objects.equals(existing.bidOpenTime(), requested.bidOpenTime())) {
            violations.add("bidOpenTime");
        }
        if (!equalsTrim(existing.ownerUnit(), requested.ownerUnit())) {
            violations.add("ownerUnit");
        }
        return violations.isEmpty()
                ? Decision.ALLOW
                : new Decision.Deny(Collections.unmodifiableList(violations),
                                    List.of("提交后不可修改：" + String.join(",", violations)));
    }

    private static boolean equalsTrim(String a, String b) {
        String x = a == null ? null : a.trim();
        String y = b == null ? null : b.trim();
        return Objects.equals(x, y);
    }

    private static void requireText(String name, String value, List<String> missing) {
        if (value == null || value.trim().isEmpty()) missing.add(name);
    }

    private static void requireNotNull(String name, Object value, List<String> missing) {
        if (value == null) missing.add(name);
    }

    private static void requirePositive(String name, Integer value, List<String> missing) {
        if (value == null || value <= 0) missing.add(name);
    }

    private static void requirePositive(String name, Long value, List<String> missing) {
        if (value == null || value <= 0L) missing.add(name);
    }

    private static void requirePositiveAmount(String name, java.math.BigDecimal value, List<String> missing) {
        if (value == null || value.signum() <= 0) missing.add(name);
    }

    /** 立项字段输入（不可变 record）。可空字段由 validate 决定是否必填。蓝图 §3.3.1.1 扩展至 29 字段。 */
    public record InitiationInput(
            String ownerUnit,
            Integer expectedBidders,
            Integer contractPeriodMonths,
            ProjectType projectType,
            CustomerType customerType,
            java.math.BigDecimal annualRevenue,
            java.math.BigDecimal annualEcommerceAmount,
            java.time.LocalDateTime bidOpenTime,
            Long ownerUserId,
            String departmentSnapshot,
            java.math.BigDecimal depositAmount,
            String depositPaymentMethod,
            String needDeposit,
            String competitors,
            String tenderAdverseItems,
            String riskAssessment,
            String riskMitigationPlan,
            String pmUnderstandsProcess,
            String supportNeeded,
            String projectPlanGap,
            String customerGrade,
            String bidStatus,
            String biddingLeaderName,
            String biddingPlatform,
            String bidResultStatus,
            String projectLeaderName,
            String leaderDepartment,
            String headquartersLocation,
            String aiRiskAssessmentNotes) {
    }

    /** Sealed Decision: Allow | Deny{missingFields, reasons}. */
    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();

        default boolean allowed() {
            return this instanceof Allow;
        }

        record Allow() implements Decision {
        }

        record Deny(List<String> missingFields, List<String> reasons) implements Decision {
            public String reasonText() {
                if (!reasons.isEmpty()) return String.join("；", reasons);
                return "缺少必填字段：" + String.join(",", missingFields);
            }
        }
    }
}
