package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.MarginDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

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
                .contains("STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.actualPaymentDate')), 1, 10), ''), '%Y-%m-%d')");
        assertThat(sql)
                .as("禁止用 CAST(...) AS DATETIME 解析 JSON 日期字段"
                  + "（空字符串会抛 SQL 异常，'0000-00-00' 会触发 JDBC zero-date 异常）")
                .doesNotContain("CAST(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.actualPaymentDate')), 1, 10) AS DATETIME)");
    }

    @Test
    void derivedSelectFees_usesStrToDateWithNullIf_notCastAsDatetime_forExpectedRefundDate() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        assertThat(sql)
                .as("expectedRefundDate 必须用 STR_TO_DATE(NULLIF(..., ''), '%Y-%m-%d') 解析")
                .contains("STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d')");
        assertThat(sql)
                .as("禁止用 CAST(...) AS DATETIME 解析 expectedRefundDate JSON 字段")
                .doesNotContain("CAST(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.expectedRefundDate')), 1, 10) AS DATETIME)");
    }

    @Test
    void summaryBase_usesStrToDateWithNullIf_notCastAsDatetime_forExpectedRefundDate() {
        String sql = MarginQuerySupport.summaryBase(MarginQueryRole.ADMIN).toString();
        assertThat(sql)
                .as("summaryBase 中 expectedRefundDate 必须用 STR_TO_DATE(NULLIF(..., ''), '%Y-%m-%d') 解析")
                .contains("STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d')");
        assertThat(sql)
                .as("禁止用 CAST AS DATETIME 解析 summaryBase 中的 expectedRefundDate")
                .doesNotContain("CAST(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.expectedRefundDate')), 1, 10) AS DATETIME)");
    }

    @Test
    void derivedSelectFees_preservesCoalesceFallback_toFeesColumns() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        // XIYU-P： fees.payment_date / fees.fee_date 历史数据可能残留 zero date，
        // 用 NULLIF 转成 NULL 后再参与 COALESCE，避免 JDBC 抛 DataException。
        assertThat(sql)
                .as("payment_date 必须保留 COALESCE 回退到 NULLIF(f.payment_date, '0000-00-00 00:00:00')")
                .contains("COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.actualPaymentDate')), 1, 10), ''), '%Y-%m-%d'), NULLIF(f.payment_date, '0000-00-00 00:00:00'))");
        assertThat(sql)
                .as("exp_return_date 必须保留 COALESCE 回退到 NULLIF(f.fee_date, '0000-00-00 00:00:00')")
                .contains("COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d'), NULLIF(f.fee_date, '0000-00-00 00:00:00'))");
    }

    @Test
    void summaryBase_preservesCoalesceFallback_toFeeDate() {
        String sql = MarginQuerySupport.summaryBase(MarginQueryRole.ADMIN).toString();
        assertThat(sql)
                .as("summaryBase exp_return_date 必须保留 COALESCE 回退到 NULLIF(f.fee_date, '0000-00-00 00:00:00')")
                .contains("COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d'), NULLIF(f.fee_date, '0000-00-00 00:00:00'))");
    }

    @Test
    void derivedSelectFees_wrapsDepositReturnDate_withNullIfZeroDate() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        assertThat(sql)
                .as("actual_return_date 必须用 NULLIF 处理 zero date，防止 JDBC DataException")
                .contains("NULLIF(pc.deposit_return_date, '0000-00-00 00:00:00') as actual_return_date");
    }

    @Test
    void derivedSelectInit_wrapsDepositReturnDate_withNullIfZeroDate() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("INIT 分支 actual_return_date 也必须用 NULLIF 处理 zero date")
                .contains("NULLIF(pc.deposit_return_date, '0000-00-00 00:00:00') as actual_return_date");
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
                  + "u.full_name=unicode_ci，UNION 必须 collation 一致）"
                  + "CO-490: 加 u.full_name 兜底 + NULLIF 空字符串包裹，"
                  + "修复 pid/tender 都空（或空字符串）时项目负责人显示空")
                .contains("COALESCE(NULLIF(pid.project_leader_name, ''),"
                        + "       NULLIF(t.project_manager_name, ''), u.full_name)"
                        + "       COLLATE utf8mb4_unicode_ci as project_leader_name");
    }

    @Test
    void derivedSelectFees_biddingLeaderName_hasCollateUnicodeCi() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        assertThat(sql)
                .as("DERIVED_SELECT_FEES bidding_leader_name 必须加 COLLATE utf8mb4_unicode_ci"
                  + "（u_lead.full_name/pid.bidding_leader_name/t.bidding_person_name 跨表 collation 一致）"
                  + "CO-490: NULLIF 包裹空字符串；CO-507: 优先取 ProjectLeadAssignment 关联的 u_lead.full_name")
                .contains("COALESCE(NULLIF(u_lead.full_name, ''),"
                        + "       NULLIF(pid.bidding_leader_name, ''),"
                        + "       NULLIF(t.bidding_person_name, ''))"
                        + "       COLLATE utf8mb4_unicode_ci as bidding_leader_name");
    }

    @Test
    void derivedSelectInit_projectLeaderName_hasCollateUnicodeCi() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("DERIVED_SELECT_INIT project_leader_name 必须加 COLLATE utf8mb4_unicode_ci"
                  + "（UNION ALL 的两个分支必须 collation 一致）"
                  + "CO-490: 加 u.full_name 兜底 + NULLIF 空字符串包裹，与 FEES 分支对齐")
                .contains("COALESCE(NULLIF(pid.project_leader_name, ''),"
                        + "       NULLIF(t.project_manager_name, ''), u.full_name)"
                        + "       COLLATE utf8mb4_unicode_ci as project_leader_name");
    }

    @Test
    void derivedSelectInit_biddingLeaderName_hasCollateUnicodeCi() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("DERIVED_SELECT_INIT bidding_leader_name 必须加 COLLATE utf8mb4_unicode_ci"
                  + "（UNION ALL 的两个分支必须 collation 一致）"
                  + "CO-490: NULLIF 包裹空字符串；CO-507: 优先取 u_lead.full_name，与 FEES 分支对齐")
                .contains("COALESCE(NULLIF(u_lead.full_name, ''),"
                        + "       NULLIF(pid.bidding_leader_name, ''),"
                        + "       NULLIF(t.bidding_person_name, ''))"
                        + "       COLLATE utf8mb4_unicode_ci as bidding_leader_name");
    }

    // ── CO-490 新增：INIT 分支取任务 JSON 字段（修复前硬编码 NULL）─────────────

    @Test
    void derivedSelectInit_usesStrToDateWithNullIf_forActualPaymentDate_notNullLiteral() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("CO-490: INIT 分支 payment_date 必须从任务 JSON actualPaymentDate 取值"
                  + "（STR_TO_DATE(NULLIF(...)) 解析），禁止硬编码 NULL")
                .contains("STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.actualPaymentDate')), 1, 10), ''), '%Y-%m-%d') as payment_date");
        assertThat(sql)
                .as("CO-490: INIT 分支禁止保留旧的 NULL as payment_date 硬编码")
                .doesNotContain("NULL as payment_date");
    }

    @Test
    void derivedSelectInit_usesStrToDateWithNullIf_forExpectedRefundDate_notNullLiteral() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("CO-490: INIT 分支 exp_return_date 必须从任务 JSON expectedRefundDate 取值"
                  + "（STR_TO_DATE(NULLIF(...)) 解析），禁止硬编码 NULL")
                .contains("STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d') as exp_return_date");
    }

    @Test
    void derivedSelectInit_usesJsonExtract_forPayeeAndAccount_notNullLiteral() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("CO-490: INIT 分支 payee_name 必须从任务 JSON payee 取值（NULLIF 包裹空字符串），"
                  + "禁止硬编码 NULL")
                .contains("NULLIF(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.payee')), '')"
                        + " as payee_name");
        assertThat(sql)
                .as("CO-490: INIT 分支 payee_account 必须从任务 JSON payeeAccount 取值，"
                  + "禁止硬编码 NULL")
                .contains("JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.payeeAccount'))"
                        + " as payee_account");
    }

    @Test
    void derivedSelectInit_usesProjectClosureForReturnedAmount_notNullLiteral() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        // CO-508 抽取 returnedAmountExpr("pid.deposit_amount") 共享方法后，
        // 格式从多行改为单行，但语义不变：必须从 project_closure 取值，禁止硬编码 NULL。
        assertThat(sql)
                .as("CO-490: INIT 分支 returned_amount 必须从 project_closure 取值"
                  + "（FULLY_RETURNED → pid.deposit_amount, "
                  + "PARTIAL_RETURN_PARTIAL_TRANSFER → pc.returned_amount），禁止硬编码 NULL")
                .contains("WHEN pc.deposit_return_status = 'FULLY_RETURNED'"
                        + " THEN pid.deposit_amount")
                .contains("WHEN pc.deposit_return_status = 'PARTIAL_RETURN_PARTIAL_TRANSFER'"
                        + " THEN pc.returned_amount");
    }

    // ── CO-490 新增：deposit_payment_method CASE WHEN 翻译（FEES + INIT 共用）──

    @Test
    void derivedSelectFees_translatesDepositPaymentMethod_viaCaseWhen() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        assertThat(sql)
                .as("CO-490: FEES 分支 deposit_payment_method 必须用 CASE WHEN 翻译"
                  + "（WIRE→电汇, GUARANTEE→保险/保函），禁止直接透传枚举英文")
                .contains("CASE pid.deposit_payment_method"
                        + "  WHEN 'WIRE' THEN '电汇'"
                        + "  WHEN 'GUARANTEE' THEN '保险/保函'"
                        + "  ELSE pid.deposit_payment_method"
                        + " END as deposit_payment_method");
        assertThat(sql)
                .as("CO-490: FEES 分支禁止直接透传 pid.deposit_payment_method as deposit_payment_method")
                .doesNotContain("pid.deposit_payment_method, NULL as payee_name");
    }

    @Test
    void derivedSelectInit_translatesDepositPaymentMethod_viaCaseWhen() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_INIT;
        assertThat(sql)
                .as("CO-490: INIT 分支 deposit_payment_method 必须用 CASE WHEN 翻译"
                  + "（与 FEES 分支一致）")
                .contains("CASE pid.deposit_payment_method"
                        + "  WHEN 'WIRE' THEN '电汇'"
                        + "  WHEN 'GUARANTEE' THEN '保险/保函'"
                        + "  ELSE pid.deposit_payment_method"
                        + " END as deposit_payment_method");
    }

    // ── CO-490 新增：payee NULLIF 包裹（FEES 分支，与日期字段一致）─────────

    @Test
    void derivedSelectFees_wrapsPayeeWithNullIf_forEmptyStringFallback() {
        String sql = MarginDerivedTableColumns.DERIVED_SELECT_FEES;
        assertThat(sql)
                .as("CO-490: FEES 分支 payee 必须用 NULLIF(..., '') 包裹，"
                  + "避免任务 JSON 空字符串导致 COALESCE 不回退到 f.return_to")
                .contains("COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),"
                        + " '$.payee')), ''), f.return_to) as payee_name");
    }

    // ── Sentry XIYU-P 回归：JSON_EXTRACT 输入必须防御空字符串 ────────────────
    //
    // 根因：tasks.extended_fields_json 存在空字符串 ''，MySQL JSON_EXTRACT('', '$.x')
    // 直接抛 "Invalid JSON text: The document is empty" → 保证金列表 500。
    // 上一轮 CO-490 只包裹了 STR_TO_DATE/CAST 的结果，没包裹 JSON_EXTRACT 的输入。

    @Test
    void allJsonExtractCalls_guardEmptyString_withNullIf() {
        String listSql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN).toString();
        String countSql = MarginQuerySupport.countBase(MarginQueryRole.ADMIN).toString();
        String summarySql = MarginQuerySupport.summaryBase(MarginQueryRole.ADMIN).toString();

        for (String sql : List.of(listSql, countSql, summarySql)) {
            String normalized = sql.replace("JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''),", "");
            assertThat(normalized)
                    .as("所有 JSON_EXTRACT(dt.extended_fields_json, ...) 必须用 NULLIF(..., '') 包裹，"
                      + "防止空字符串触发 MySQL 'Invalid JSON text: The document is empty'")
                    .doesNotContain("JSON_EXTRACT(dt.extended_fields_json,");
        }
    }

    // ── Sentry XIYU-T 回归：mapRow 日期列禁止强转 (Timestamp)，必须走 toLdt ──────
    //
    // 根因（CO-508 回归）：派生表 exp_return_date 列在 UNION ALL 两分支下：
    //   FEES: COALESCE(STR_TO_DATE(...), NULLIF(f.fee_date, '0000-00-00 00:00:00'))
    //   INIT: STR_TO_DATE(...)
    // 因 NULLIF 中含 string literal '0000-00-00 00:00:00'，MySQL UNION ALL
    // 类型聚合可能把整列推回成 char(19)，JDBC 读到 String。
    // mapRow 中 label((Timestamp) r[C_EXP_RETURN], ...) 直接强转 → ClassCastException。
    //
    // 修复：label 签名 Timestamp → LocalDateTime，mapRow 调用点走 toLdt 防御性转换。
    //
    // 本测试覆盖 3 种 JDBC 返回类型：String / null / Timestamp，确保都不抛 CCE。

    /**
     * 构造一个 16 列的 Object[]，仅 C_EXP_RETURN (idx=11) 由调用方传入。
     * 其他列填合法值（金额 1000，退回 0，服务费 0 → 不命中规则3，走规则1/2）。
     */
    private static Object[] rowWithExpReturn(final Object expReturn) {
        return new Object[]{
                1L,                                          // C_FEE_ID
                100L,                                        // C_PROJ_ID
                "测试项目",                                  // C_PROJ_NAME
                "西域",                                      // C_OWNER
                "张三",                                      // C_PROJ_LEAD
                "李四",                                      // C_BID_LEAD
                new BigDecimal("1000"),                     // C_AMT
                null,                                        // C_PAY_DATE
                "电汇",                                      // C_PAY_METHOD
                "收款方",                                    // C_PAYEE
                "账号",                                      // C_PAYEE_ACCT
                expReturn,                                   // C_EXP_RETURN (idx=11)
                BigDecimal.ZERO,                             // C_RET_AMT
                BigDecimal.ZERO,                             // C_SVC_FEE
                null,                                        // C_ACT_RETURN
                "PENDING"                                    // C_STATUS
        };
    }

    @Test
    void mapRow_doesNotThrowClassCast_whenExpReturnDateIsString() {
        // Sentry XIYU-T 复发场景：UNION ALL 把 exp_return_date 推导成 char(19)，
        // JDBC 拿到 String。修复前 label((Timestamp) r[C_EXP_RETURN]) 直接抛 CCE。
        Object[] row = rowWithExpReturn("2025-12-31 00:00:00");
        MarginDTO dto = MarginQuerySupport.mapRow(row);
        assertThat(dto).as("String 类型 exp_return_date 不应抛 ClassCastException").isNotNull();
        assertThat(dto.getStatusLabel())
                .as("String 走 toLdt 返回 null → 不命中规则2 → 默认「未到期」")
                .isEqualTo("未到期");
    }

    @Test
    void mapRow_doesNotThrowClassCast_whenExpReturnDateIsNull() {
        // INIT 分支无 fees 记录时 exp_return_date 为 NULL。
        Object[] row = rowWithExpReturn(null);
        MarginDTO dto = MarginQuerySupport.mapRow(row);
        assertThat(dto).as("null 类型 exp_return_date 不应抛 ClassCastException").isNotNull();
        assertThat(dto.getStatusLabel()).isEqualTo("未到期");
    }

    @Test
    void mapRow_doesNotThrowClassCast_whenExpReturnDateIsTimestamp() {
        // 正常场景：JDBC 返回 Timestamp，确保修复后行为不回归。
        Object[] row = rowWithExpReturn(Timestamp.valueOf(LocalDateTime.of(2025, 12, 31, 0, 0)));
        MarginDTO dto = MarginQuerySupport.mapRow(row);
        assertThat(dto).as("Timestamp 类型 exp_return_date 不应抛 ClassCastException").isNotNull();
        // 2025-12-31 在测试运行当下通常是过去日期 → 命中规则2「已超期」；
        // 但若 CI 在 2025-12-31 当天之前运行则「未到期」。两种都是合法输出，只断言不抛。
        assertThat(dto.getStatusLabel()).isIn("已超期", "未到期");
    }

    @Test
    void mapRow_statusLabelReturned_whenAmountMatchesRule3() {
        // 规则3：退回 + 服务费 = 保证金 → 已退回（不依赖 exp_return_date 类型）
        Object[] row = new Object[]{
                1L, 100L, "测试项目", "西域", "张三", "李四",
                new BigDecimal("1000"), null, "电汇", "收款方", "账号",
                "2025-12-31 00:00:00",                              // String 类型 exp_return_date
                new BigDecimal("600"),                              // returned_amount
                new BigDecimal("400"),                              // service_fee_amount
                null, "PENDING"
        };
        MarginDTO dto = MarginQuerySupport.mapRow(row);
        assertThat(dto.getStatusLabel())
                .as("String 类型 exp_return_date 下规则3金额匹配应命中「已退回」")
                .isEqualTo("已退回");
    }
}
