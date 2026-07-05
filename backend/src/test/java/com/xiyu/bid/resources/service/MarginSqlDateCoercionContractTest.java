package com.xiyu.bid.resources.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 字符串契约测试：保证金派生表日期强转必须用 STR_TO_DATE(NULLIF(..., ''), '%Y-%m-%d')。
 *
 * <p>根因（CO-490 回归）：前端 TaskDepositFields.vue 在未填写时把
 * actualPaymentDate / expectedRefundDate 写成空字符串 ""。
 * JSON_EXTRACT 返回 JSON 字符串 {@code ""}，JSON_UNQUOTE 变成 SQL 空字符串，
 * CAST('' AS DATETIME) 在 MySQL 8.0 严格模式下抛
 * "Incorrect datetime value: ''" → 接口 500。
 *
 * <p>修复要点（两道防线缺一不可）：
 * <ol>
 *   <li>STR_TO_DATE 替代 CAST AS DATETIME：STR_TO_DATE 解析失败返回 NULL（不抛异常），
 *       COALESCE 自然回退到 f.payment_date / f.fee_date。
 *       但 STR_TO_DATE 非法输入（如 'abc'）才返回 NULL。</li>
 *   <li>NULLIF(..., '') 包裹：MySQL 8.0 中 STR_TO_DATE('', '%Y-%m-%d')
 *       返回 '0000-00-00'（非 NULL！），会让 JDBC 驱动抛
 *       "Zero date value prohibited"。NULLIF 把空字符串转成 NULL，
 *       让 STR_TO_DATE(NULL, ...) 返回 NULL。</li>
 * </ol>
 *
 * <p>覆盖的三种边界：
 * <ul>
 *   <li>空字符串 {@code ""}（前端默认值）→ NULLIF → NULL → STR_TO_DATE → NULL → COALESCE 回退</li>
 *   <li>NULL（JSON 字段缺失）→ NULLIF(NULL, '') → NULL → STR_TO_DATE → NULL → COALESCE 回退</li>
 *   <li>非法格式 {@code "abc"} → NULLIF 透传 → STR_TO_DATE 解析失败 → NULL → COALESCE 回退</li>
 *   <li>合法格式 {@code "2026-08-15"} → NULLIF 透传 → STR_TO_DATE 解析成功 → COALESCE 用此值</li>
 * </ul>
 *
 * <p>本测试无 MySQL 容器依赖，覆盖 SQL 字符串本身，CI 必跑。
 * 行为层验证见 {@link MarginQuerySupportMysqlIntegrationTest}。
 */
class MarginSqlDateCoercionContractTest {

    @Test
    void derivedSelectFees_usesStrToDateWithNullIf_notCastAsDatetime_forActualPaymentDate() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        // 必须用 STR_TO_DATE + NULLIF 解析 actualPaymentDate，禁止 CAST AS DATETIME
        assertThat(sql)
                .as("actualPaymentDate 必须用 STR_TO_DATE(NULLIF(..., ''), '%Y-%m-%d') 解析"
                  + "（空字符串回退 NULL，不抛异常，不返回 zero date）")
                .contains("STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.actualPaymentDate')), 1, 10), ''), '%Y-%m-%d')");
        assertThat(sql)
                .as("禁止用 CAST(...) AS DATETIME 解析 JSON 日期字段"
                  + "（空字符串会抛 SQL 异常，'0000-00-00' 会触发 JDBC zero-date 异常）")
                .doesNotContain("CAST(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.actualPaymentDate')), 1, 10) AS DATETIME)");
    }

    @Test
    void derivedSelectFees_usesStrToDateWithNullIf_notCastAsDatetime_forExpectedRefundDate() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        assertThat(sql)
                .as("expectedRefundDate 必须用 STR_TO_DATE(NULLIF(..., ''), '%Y-%m-%d') 解析")
                .contains("STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d')");
        assertThat(sql)
                .as("禁止用 CAST(...) AS DATETIME 解析 expectedRefundDate JSON 字段")
                .doesNotContain("CAST(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.expectedRefundDate')), 1, 10) AS DATETIME)");
    }

    @Test
    void summaryBase_usesStrToDateWithNullIf_notCastAsDatetime_forExpectedRefundDate() {
        String sql = MarginQuerySupport.summaryBase(MarginQueryRole.ADMIN).toString();
        assertThat(sql)
                .as("summaryBase 中 expectedRefundDate 必须用 STR_TO_DATE(NULLIF(..., ''), '%Y-%m-%d') 解析")
                .contains("STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d')");
        assertThat(sql)
                .as("禁止用 CAST AS DATETIME 解析 summaryBase 中的 expectedRefundDate")
                .doesNotContain("CAST(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.expectedRefundDate')), 1, 10) AS DATETIME)");
    }

    @Test
    void derivedSelectFees_preservesCoalesceFallback_toFeesColumns() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        // COALESCE 回退链必须保留：任务 JSON 解析失败 → f.payment_date / f.fee_date
        assertThat(sql)
                .as("payment_date 必须保留 COALESCE 回退到 f.payment_date")
                .contains("COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.actualPaymentDate')), 1, 10), ''), '%Y-%m-%d'), f.payment_date)");
        assertThat(sql)
                .as("exp_return_date 必须保留 COALESCE 回退到 f.fee_date")
                .contains("COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d'), f.fee_date)");
    }

    @Test
    void summaryBase_preservesCoalesceFallback_toFeeDate() {
        String sql = MarginQuerySupport.summaryBase(MarginQueryRole.ADMIN).toString();
        assertThat(sql)
                .as("summaryBase exp_return_date 必须保留 COALESCE 回退到 f.fee_date")
                .contains("COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json,"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d'), f.fee_date)");
    }

    // ── CO-XXX UNION collation 冲突回归测试（防复发）─────────────────
    //
    // 背景：project_initiation_details.project_leader_name 使用 db default collation
    // (utf8mb4_0900_ai_ci)，而 tenders.project_manager_name 使用 utf8mb4_unicode_ci。
    // COALESCE(pid.project_leader_name, t.project_manager_name) 跨表取值时，
    // MySQL UNION 编译无法合并不同 collation → "Illegal mix of collations" → 500。
    //
    // 修复：DERIVED_SELECT_FEES 和 DERIVED_SELECT_INIT 的 project_leader_name /
    // bidding_leader_name 列必须加 COLLATE utf8mb4_unicode_ci，确保 UNION 两边
    // collation 一致。
    //
    // 本测试覆盖 4 处 COLLATE 子句（2 列 × 2 分支），防止未来修改 SQL 时误删。

    @Test
    void derivedSelectFees_projectLeaderName_hasCollateUnicodeCi() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        assertThat(sql)
                .as("DERIVED_SELECT_FEES project_leader_name 必须加 COLLATE utf8mb4_unicode_ci"
                  + "（pid.project_leader_name=0900_ai_ci, t.project_manager_name=unicode_ci，"
                  + "UNION 必须 collation 一致）")
                .contains("COALESCE(pid.project_leader_name, t.project_manager_name)"
                        + "       COLLATE utf8mb4_unicode_ci as project_leader_name");
    }

    @Test
    void derivedSelectFees_biddingLeaderName_hasCollateUnicodeCi() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        assertThat(sql)
                .as("DERIVED_SELECT_FEES bidding_leader_name 必须加 COLLATE utf8mb4_unicode_ci"
                  + "（pid.bidding_leader_name=0900_ai_ci, t.bidding_person_name=unicode_ci，"
                  + "UNION 必须 collation 一致）")
                .contains("COALESCE(pid.bidding_leader_name, t.bidding_person_name)"
                        + "       COLLATE utf8mb4_unicode_ci as bidding_leader_name");
    }

    @Test
    void derivedSelectInit_projectLeaderName_hasCollateUnicodeCi() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("DERIVED_SELECT_INIT project_leader_name 必须加 COLLATE utf8mb4_unicode_ci"
                  + "（UNION ALL 的两个分支必须 collation 一致）")
                .contains("COALESCE(pid.project_leader_name, t.project_manager_name)"
                        + "       COLLATE utf8mb4_unicode_ci as project_leader_name");
    }

    @Test
    void derivedSelectInit_biddingLeaderName_hasCollateUnicodeCi() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("DERIVED_SELECT_INIT bidding_leader_name 必须加 COLLATE utf8mb4_unicode_ci"
                  + "（UNION ALL 的两个分支必须 collation 一致）")
                .contains("COALESCE(pid.bidding_leader_name, t.bidding_person_name)"
                        + "       COLLATE utf8mb4_unicode_ci as bidding_leader_name");
    }
}
