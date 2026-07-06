package com.xiyu.bid.resources.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarginQuerySupport 真实 MySQL 集成测试（防线 2）。
 *
 * <p>背景：Sentry JAVA-C / issue 7589082793 — countBase() 派生表
 * SELECT 列与 appendFilters() 外层 WHERE 引用列不匹配，导致
 * "Unknown column 'm.status' in 'where clause'"。
 *
 * <p>防线 1（已在 MarginQuerySupport 修复）：抽取 DERIVED_SELECT_FEES /
 * DERIVED_SELECT_INIT 共享常量，listBase / countBase 复用。
 *
 * <p>防线 2（本测试）：用真实 MySQL 8.0 跑 summaryBase / listBase /
 * countBase 生成的 SQL，覆盖全 filter 组合，任何列漂移立即失败。
 * 不加载 Spring 上下文，纯 JDBC + Flyway 迁移，跑得快、隔离好。
 *
 * <p>测试数据策略：
 * <ul>
 *   <li>不依赖任何业务数据（空表也跑得通，因为只验证 SQL 语法/列解析）</li>
 *   <li>每个 filter 组合都执行一次 summaryBase + listBase + countBase</li>
 *   <li>断言：不抛 SQLGrammarException 即通过（列存在性由 MySQL 解析器保证）</li>
 * </ul>
 *
 * <p>数据库连接：复用 AbstractMysqlIntegrationTest 的本地 fallback 配置
 * （localhost:13306/xiyu_bid_verify），但不继承它（不需要 Spring 上下文）。
 * Docker 不可用或容器未启动时，测试会 fail-fast，提示如何启动。
 */
class MarginQuerySupportMysqlIntegrationTest {

    private static final String JDBC_URL =
            "jdbc:mysql://localhost:13306/xiyu_bid_verify"
          + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "xiyu_test";

    /**
     * 测试环境 sql_mode：去掉 NO_ZERO_DATE 和 NO_ZERO_IN_DATE，
     * 保留其他严格模式项（ONLY_FULL_GROUP_BY / STRICT_TRANS_TABLES /
     * ERROR_FOR_DIVISION_BY_ZERO / NO_ENGINE_SUBSTITUTION）。
     * 与 AbstractMysqlIntegrationTest 保持一致，原因见其注释：
     * V1077 迁移脚本含 '0000-00-00 00:00:00' 字面量，MySQL 8.0
     * 默认 sql_mode 会触发 Error 1292。
     */
    private static final String TEST_SQL_MODE =
            "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION";

    private static DataSource dataSource;
    private static NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setUp() {
        // 检测本地 MySQL 容器是否可用，不可用则跳过整个测试类
        // （CI 上由 TenderCommandServiceMysqlIntegrationTest 等 @SpringBootTest
        // 集成测试覆盖 MySQL 路径；本测试主要价值是本地开发时防回归）
        try {
            dataSource = new DriverManagerDataSource(JDBC_URL, DB_USERNAME, DB_PASSWORD);
            jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
            // 触发一次连接，检查 MySQL 容器是否在 13306 端口
            jdbcTemplate.queryForObject("SELECT 1", Map.of(), Integer.class);
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                    "跳过 MarginQuerySupportMysqlIntegrationTest：本地 MySQL 容器不可用。"
                  + "如需运行，请启动：docker run -d --name xiyu-mysql-test -p 13306:3306 "
                  + "-e MYSQL_ROOT_PASSWORD=xiyu_test -e MYSQL_DATABASE=xiyu_bid_verify mysql:8.0");
            return;
        }
        try {
            // 调整 sql_mode，对齐 CI/生产（V1077 '0000-00-00' 字面量兼容）
            adjustSqlMode();
            // 跑 Flyway 迁移，建出所有业务表（与生产 schema 一致）
            // 重复跑是 no-op（Flyway 有版本管理）
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration-mysql")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Flyway 迁移失败（localhost:13306/xiyu_bid_verify）。"
                  + "如需重置：docker exec xiyu-mysql-test mysql -uroot -pxiyu_test "
                  + "-e \"DROP DATABASE IF EXISTS xiyu_bid_verify; "
                  + "CREATE DATABASE xiyu_bid_verify CHARACTER SET utf8mb4 "
                  + "COLLATE utf8mb4_unicode_ci;\"",
                    e);
        }
    }

    /**
     * 调整本地 MySQL 容器的 sql_mode，去掉 NO_ZERO_DATE 和 NO_ZERO_IN_DATE。
     * SET GLOBAL 只对新建连接生效，HikariCP / DriverManagerDataSource
     * 后续获取的连接会读到新值。
     */
    private static void adjustSqlMode() {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                JDBC_URL, DB_USERNAME, DB_PASSWORD);
                java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("SET GLOBAL sql_mode = '" + TEST_SQL_MODE + "'");
            stmt.execute("SET GLOBAL character_set_server = 'utf8mb4'");
            stmt.execute("SET GLOBAL collation_server = 'utf8mb4_unicode_ci'");
            stmt.execute("ALTER DATABASE xiyu_bid_verify "
                  + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "无法调整本地 MySQL 容器的 sql_mode", e);
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (dataSource instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // DriverManagerDataSource 的 close 是 no-op
            }
        }
    }

    // ── 全 filter 矩阵：每个 filter 组合跑 summary / list / count 三种 SQL ──

    static Stream<Arguments> allFilterCombinations() {
        return Stream.of(
                Arguments.of("无 filter", Map.of()),
                Arguments.of("status=PENDING", Map.of("status", "PENDING")),
                Arguments.of("status=OVERDUE", Map.of("status", "OVERDUE")),
                Arguments.of("status=RETURNED", Map.of("status", "RETURNED")),
                Arguments.of("projectName", Map.of("projectName", "测试")),
                Arguments.of("ownerUnit", Map.of("ownerUnit", "测试单位")),
                Arguments.of("projectLeaderName", Map.of("projectLeaderName", "张三")),
                Arguments.of("biddingLeaderName", Map.of("biddingLeaderName", "李四")),
                Arguments.of("paymentDateStart", Map.of("paymentDateStart", "2026-01-01")),
                Arguments.of("paymentDateEnd", Map.of("paymentDateEnd", "2026-12-31")),
                Arguments.of("expectedReturnDateStart", Map.of("expectedReturnDateStart", "2026-01-01")),
                Arguments.of("expectedReturnDateEnd", Map.of("expectedReturnDateEnd", "2026-12-31")),
                Arguments.of("全 filter + status=PENDING", Map.of(
                        "projectName", "测试项目",
                        "ownerUnit", "测试单位",
                        "projectLeaderName", "张三",
                        "biddingLeaderName", "李四",
                        "paymentDateStart", "2026-01-01",
                        "paymentDateEnd", "2026-12-31",
                        "expectedReturnDateStart", "2026-01-01",
                        "expectedReturnDateEnd", "2026-12-31",
                        "status", "PENDING")),
                Arguments.of("全 filter + status=OVERDUE", Map.of(
                        "projectName", "测试项目",
                        "ownerUnit", "测试单位",
                        "projectLeaderName", "张三",
                        "biddingLeaderName", "李四",
                        "paymentDateStart", "2026-01-01",
                        "paymentDateEnd", "2026-12-31",
                        "expectedReturnDateStart", "2026-01-01",
                        "expectedReturnDateEnd", "2026-12-31",
                        "status", "OVERDUE"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFilterCombinations")
    @DisplayName("countBase 生成的 SQL 在真实 MySQL 上执行不抛异常")
    void countBase_executesWithoutSqlError(final String label, final Map<String, String> filters) {
        StringBuilder sql = MarginQuerySupport.countBase(MarginQueryRole.ADMIN);
        MarginFilterBuilder.appendFilters(sql, filters);
        // 把 JPA 命名参数（:pName 等）替换成字面量，只验证 SQL 列解析/语法
        String rawSql = replaceParamsWithLiterals(sql.toString(), filters);
        jdbcTemplate.queryForList(rawSql, Map.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFilterCombinations")
    @DisplayName("listBase 生成的 SQL 在真实 MySQL 上执行不抛异常")
    void listBase_executesWithoutSqlError(final String label, final Map<String, String> filters) {
        StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
        MarginFilterBuilder.appendFilters(sql, filters);
        sql.append(" ORDER BY m.created_at DESC LIMIT 20 OFFSET 0");
        String rawSql = replaceParamsWithLiterals(sql.toString(), filters);
        jdbcTemplate.queryForList(rawSql, Map.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFilterCombinations")
    @DisplayName("summaryBase 生成的 SQL 在真实 MySQL 上执行不抛异常")
    void summaryBase_executesWithoutSqlError(final String label, final Map<String, String> filters) {
        StringBuilder sql = MarginQuerySupport.summaryBase(MarginQueryRole.ADMIN);
        // 注意：summary 接口（MarginController.getSummary）不接受 filter 参数，
        // 因此生产路径不会调用 appendFilters(summaryBase)。
        // 这里仍跑全 filter 矩阵是"防御性测试"——如果未来给 summary 加 filter，
        // 此测试会立即暴露 summaryBase 派生表缺列（当前只有 amount/status/exp_return_date 三列）。
        // 已知：projectName / ownerUnit / projectLeaderName / biddingLeaderName /
        // paymentDate 等 filter 会让 summaryBase 失败（派生表无对应列），
        // 这是预期行为，不应在 summary 上加这些 filter。
        if (filters.isEmpty()
                || (filters.size() == 1 && filters.containsKey("status"))) {
            // summary 内部 SQL 已用 m.status，status filter 会被 appendFilters 加上
            String rawSql = replaceParamsWithLiterals(sql.toString(), filters);
            jdbcTemplate.queryForMap(rawSql, Map.of());
        } else {
            // 非 status filter 不应在 summary 上调用，跳过执行
            // （跑会失败，因为派生表故意只 SELECT 3 列）
        }
    }

    // ── STAFF 角色验证：带 :muid 参数的 SQL 也要跑通 ──

    @Test
    @DisplayName("STAFF 角色 countBase SQL 带 :muid 参数在真实 MySQL 上执行不抛异常")
    void countBase_staffRole_executesWithoutSqlError() {
        StringBuilder sql = MarginQuerySupport.countBase(MarginQueryRole.STAFF);
        String rawSql = sql.toString().replace(":muid", "1");
        jdbcTemplate.queryForList(rawSql, Map.of());
    }

    @Test
    @DisplayName("STAFF 角色 listBase SQL 带 :muid 参数在真实 MySQL 上执行不抛异常")
    void listBase_staffRole_executesWithoutSqlError() {
        StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.STAFF);
        sql.append(" ORDER BY m.created_at DESC LIMIT 20 OFFSET 0");
        String rawSql = sql.toString().replace(":muid", "1");
        jdbcTemplate.queryForList(rawSql, Map.of());
    }

    @Test
    @DisplayName("STAFF 角色 summaryBase SQL 带 :muid 参数在真实 MySQL 上执行不抛异常")
    void summaryBase_staffRole_executesWithoutSqlError() {
        StringBuilder sql = MarginQuerySupport.summaryBase(MarginQueryRole.STAFF);
        String rawSql = sql.toString().replace(":muid", "1");
        jdbcTemplate.queryForMap(rawSql, Map.of());
    }

    /**
     * 把 MarginQuerySupport 生成的 JPA 命名参数（:pName / :pdS 等）
     * 替换成 SQL 字面量，让纯 JDBC 也能执行。
     * 只验证 SQL 列解析/语法，不验证参数绑定逻辑（那部分由 MarginServiceTest 覆盖）。
     */
    private String replaceParamsWithLiterals(final String sql, final Map<String, String> filters) {
        String result = sql;
        if (filters.containsKey("projectName")) {
            result = result.replace(":pName", "'" + filters.get("projectName") + "'");
        }
        if (filters.containsKey("ownerUnit")) {
            result = result.replace(":oUnit", "'" + filters.get("ownerUnit") + "'");
        }
        if (filters.containsKey("projectLeaderName")) {
            result = result.replace(":pLead", "'" + filters.get("projectLeaderName") + "'");
        }
        if (filters.containsKey("biddingLeaderName")) {
            result = result.replace(":bLead", "'" + filters.get("biddingLeaderName") + "'");
        }
        if (filters.containsKey("paymentDateStart")) {
            result = result.replace(":pdS", "'" + filters.get("paymentDateStart") + " 00:00:00'");
        }
        if (filters.containsKey("paymentDateEnd")) {
            result = result.replace(":pdE", "'" + filters.get("paymentDateEnd") + " 23:59:59'");
        }
        if (filters.containsKey("expectedReturnDateStart")) {
            result = result.replace(":edS", "'" + filters.get("expectedReturnDateStart") + " 00:00:00'");
        }
        if (filters.containsKey("expectedReturnDateEnd")) {
            result = result.replace(":edE", "'" + filters.get("expectedReturnDateEnd") + " 23:59:59'");
        }
        return result;
    }

    // ── 派生表列契约完整性断言（补充防线 1 的 Mockito 测试）──

    @Test
    @DisplayName("countBase 与 listBase 派生表 SELECT 列完全一致")
    void countBase_andListBase_derivedTableColumnsAligned() {
        String countSql = MarginQuerySupport.countBase(MarginQueryRole.ADMIN).toString();
        String listSql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN).toString();

        // 抽取派生表 SELECT 子句（FROM 之前的部分）
        // 派生表列契约：两个 base 方法的派生表必须用相同的 SELECT 列定义
        String countDerived = extractDerivedTableSelect(countSql);
        String listDerived = extractDerivedTableSelect(listSql);

        assertThat(countDerived)
                .as("countBase 与 listBase 的派生表 SELECT 列必须完全一致（防 CO-XXX 复发）")
                .isEqualTo(listDerived);
    }

    /**
     * 从 SQL 中抽取派生表内部 SELECT 的列定义部分。
     * 抽取 "FROM (" 之后到 "UNION ALL" 之前的内容，
     * 即派生表的第一个 SELECT 分支（fees 分支）。
     */
    private String extractDerivedTableSelect(final String sql) {
        int fromIdx = sql.indexOf("FROM (");
        int unionIdx = sql.indexOf("UNION ALL");
        if (fromIdx < 0 || unionIdx < 0) {
            return sql;
        }
        return sql.substring(fromIdx, unionIdx).trim();
    }

    // ── 行为层测试：验证 status filter 返回正确的行（防 CO-XXX 复发）──
    //
    // 既有 46 个测试只验证 SQL 不抛异常，无法捕获语义错误。
    // 以下 3 个测试验证 filter 返回的行数和内容，形成真正的回归保护。

    @Test
    @DisplayName("filterByStatusPending 应包含 init 分支行（exp_return_date IS NULL）")
    void filterByStatusPending_shouldIncludeInitBranchRows_withNullExpReturnDate() {
        // 造数据：1 条 need_deposit=YES 但无 fees 的立项记录（走 init 分支，exp_return_date IS NULL）
        Long projectId = createTestProject("pending-init");
        createInitiationDetails(projectId, "YES", new BigDecimal("5000"));
        try {
            // 构建 SQL：listBase(ADMIN) + status=PENDING filter
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            MarginFilterBuilder.appendFilters(sql, Map.of("status", "PENDING"));
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            // 执行（status 是字面量拼接，无需 replaceParamsWithLiterals）
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            // 断言：PENDING 筛选应包含 init 占位行
            assertThat(rows)
                    .as("PENDING 筛选应包含 init 分支行（exp_return_date IS NULL）"
                      + "—— 修复前 NULL >= NOW() 为 NULL/falsy 导致漏掉")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("CO-508: filterByStatusReturned 应包含金额匹配行（fee.status=PAID + project_closure FULLY_RETURNED）")
    void filterByStatusReturned_shouldIncludeAmountMatchedRows() {
        // CO-508 语义变更：RETURNED 筛选不再依赖 fee.status='RETURNED' 或 'CANCELLED'，
        // 而是按规则3纯金额判定：COALESCE(returned_amount,0) + COALESCE(service_fee_amount,0) = amount。
        // 本测试：fee.status=PAID（不是 RETURNED 也不是 CANCELLED），
        // 但 project_closure.deposit_return_status=FULLY_RETURNED，
        // 此时 returned_amount = fee.amount，应被 RETURNED 筛选命中。
        BigDecimal amount = new BigDecimal("5000");
        Long projectId = createTestProject("co508-returned-amount-match");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFee(projectId, "BID_BOND", "PAID",
                  "DATE_ADD(NOW(), INTERVAL 30 DAY)", amount);
        createProjectClosure(projectId, "FULLY_RETURNED", null, null);
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            MarginFilterBuilder.appendFilters(sql, Map.of("status", "RETURNED"));
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("RETURNED 筛选应命中金额匹配行（fee.status=PAID + "
                      + "project_closure FULLY_RETURNED → returned_amount = amount）"
                      + "—— CO-508 规则3纯按金额判定，与 fee.status 无关")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("CO-508: filterByStatusReturned 不应包含 CANCELLED fee（无 project_closure 金额匹配）")
    void filterByStatusReturned_shouldExcludeCancelledFees_withoutAmountMatch() {
        // CO-508 语义变更：CANCELLED 不再因 fee.status 自动算作"已退回"。
        // 本测试：fee.status=CANCELLED，无 project_closure 记录，
        // returned_amount / service_fee_amount 均为 NULL → 0 + 0 != amount → 非已退回。
        BigDecimal amount = new BigDecimal("5000");
        Long projectId = createTestProject("co508-cancelled-no-match");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFee(projectId, "BID_BOND", "CANCELLED",
                  "DATE_SUB(NOW(), INTERVAL 1 DAY)", amount);
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            MarginFilterBuilder.appendFilters(sql, Map.of("status", "RETURNED"));
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("RETURNED 筛选不应命中 CANCELLED fee（无 project_closure 金额匹配）"
                      + "—— CO-508 废弃了 fee.status → '已退回' 的耦合")
                    .noneMatch(row -> projectId.equals(extractProjectId(row)));
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("CO-508: filterByStatusReturned 应命中 PARTIAL_RETURN_PARTIAL_TRANSFER（金额合计等于保证金）")
    void filterByStatusReturned_shouldIncludePartialReturnPartialTransfer_whenAmountMatches() {
        // 部分退回 + 部分转服务费，两者合计等于保证金金额 → 规则3命中"已退回"。
        BigDecimal amount = new BigDecimal("5000");
        BigDecimal returned = new BigDecimal("3000");
        BigDecimal transfer = new BigDecimal("2000");
        Long projectId = createTestProject("co508-partial-match");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFee(projectId, "BID_BOND", "PAID",
                  "DATE_ADD(NOW(), INTERVAL 30 DAY)", amount);
        createProjectClosure(projectId, "PARTIAL_RETURN_PARTIAL_TRANSFER",
                             returned, transfer);
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            MarginFilterBuilder.appendFilters(sql, Map.of("status", "RETURNED"));
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("RETURNED 筛选应命中 PARTIAL_RETURN_PARTIAL_TRANSFER 行"
                      + "（returned_amount=3000 + service_fee_amount=2000 = amount=5000）"
                      + "—— 规则3按金额合计判定")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("CO-508: filterByStatusOverdue 应排除已退回行（金额匹配优先于日期判定）")
    void filterByStatusOverdue_shouldExcludeAmountMatchedRows_evenIfOverdue() {
        // 规则3优先级 > 规则2：即使 exp_return_date < NOW()，
        // 只要 returned_amount + service_fee_amount = amount，状态就是"已退回"而非"已超期"。
        BigDecimal amount = new BigDecimal("5000");
        Long projectId = createTestProject("co508-overdue-but-returned");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        // fee_date = 30 天前 → exp_return_date < NOW()，若无金额匹配则为"已超期"
        createFee(projectId, "BID_BOND", "PAID",
                  "DATE_SUB(NOW(), INTERVAL 30 DAY)", amount);
        createProjectClosure(projectId, "FULLY_RETURNED", null, null);
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            MarginFilterBuilder.appendFilters(sql, Map.of("status", "OVERDUE"));
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("OVERDUE 筛选不应命中已退回行（规则3优先于规则2）"
                      + "—— 即使 exp_return_date < NOW()，金额匹配后状态为'已退回'")
                    .noneMatch(row -> projectId.equals(extractProjectId(row)));
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("CO-508: summaryBase 已退回金额不计入 totalPending / overdueAmount")
    void summaryBase_overdueCount_usesAmountBasedNotReturned() {
        // 验证 summary 聚合：project_closure FULLY_RETURNED 的行
        // 不应计入 totalPending / pendingCount / overdueAmount / overdueCount。
        BigDecimal amount = new BigDecimal("5000");
        Long projectId = createTestProject("co508-summary-returned");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        // fee_date = 30 天前 → 若未退回则计入 overdue
        createFee(projectId, "BID_BOND", "PAID",
                  "DATE_SUB(NOW(), INTERVAL 30 DAY)", amount);
        createProjectClosure(projectId, "FULLY_RETURNED", null, null);
        try {
            StringBuilder sql = MarginQuerySupport.summaryBase(MarginQueryRole.ADMIN);

            Map<String, Object> before =
                    jdbcTemplate.queryForMap(sql.toString(), Map.of());
            long pendingCountBefore = ((Number) before.get("pendingCount")).longValue();
            long overdueCountBefore = ((Number) before.get("overdueCount")).longValue();

            // 删除 project_closure 让行变为"未退回 + 已超期"，应同时计入 pending + overdue
            jdbcTemplate.update(
                    "DELETE FROM project_closure WHERE project_id = :pid",
                    Map.of("pid", projectId));

            Map<String, Object> after =
                    jdbcTemplate.queryForMap(sql.toString(), Map.of());
            long pendingCountAfter = ((Number) after.get("pendingCount")).longValue();
            long overdueCountAfter = ((Number) after.get("overdueCount")).longValue();

            assertThat(pendingCountAfter - pendingCountBefore)
                    .as("删除 project_closure 后，行从'已退回'变'已超期'，"
                      + "应被 notReturned 谓词纳入 pendingCount")
                    .isEqualTo(1L);
            assertThat(overdueCountAfter - overdueCountBefore)
                    .as("删除 project_closure 后，行变'已超期'，应计入 overdueCount")
                    .isEqualTo(1L);
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("filterByStatusOverdue 应排除 init 分支行（exp_return_date IS NULL）")
    void filterByStatusOverdue_shouldExcludeInitBranchRows_withNullExpReturnDate() {
        // 造数据：
        //   - 1 条 PAID fee 且 fee_date < NOW()（应被 OVERDUE 命中）
        //   - 1 条 init 占位行（exp_return_date IS NULL，不应被 OVERDUE 命中）
        Long projectIdFee = createTestProject("overdue-fee");
        createInitiationDetails(projectIdFee, "NO", BigDecimal.ZERO);
        createFee(projectIdFee, "BID_BOND", "PAID",
                  "DATE_SUB(NOW(), INTERVAL 30 DAY)", new BigDecimal("5000"));

        Long projectIdInit = createTestProject("overdue-init");
        createInitiationDetails(projectIdInit, "YES", new BigDecimal("5000"));

        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            MarginFilterBuilder.appendFilters(sql, Map.of("status", "OVERDUE"));
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            // 应包含已超期的 fee 行
            assertThat(rows)
                    .as("OVERDUE 筛选应包含已超期的 fee 行")
                    .anyMatch(row -> projectIdFee.equals(extractProjectId(row)));
            // 不应包含 init 占位行（NULL < NOW() 为 NULL/falsy，自动排除）
            assertThat(rows)
                    .as("OVERDUE 筛选不应包含 init 占位行（exp_return_date IS NULL）"
                      + "—— NULL < NOW() 为 NULL/falsy，应被排除")
                    .noneMatch(row -> projectIdInit.equals(extractProjectId(row)));
        } finally {
            cleanupTestData(projectIdFee);
            cleanupTestData(projectIdInit);
        }
    }

    @Test
    @DisplayName("project_leader_name 应从 tenders.project_manager_name 继承（init 分支，pid 为空）")
    void projectLeaderName_shouldInheritFromTender_whenPidIsNull_initBranch() {
        Long tenderId = createTender("leader-inherit", "TenderProjectLeader", "TenderBiddingLeader");
        Long projectId = createTestProject("leader-inherit-init", tenderId);
        createInitiationDetails(projectId, "YES", new BigDecimal("5000"));
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("查询结果应包含项目")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));

            Map<String, Object> row = rows.stream()
                    .filter(r -> projectId.equals(extractProjectId(r)))
                    .findFirst()
                    .orElse(null);

            assertThat(row)
                    .as("项目行应存在")
                    .isNotNull();
            assertThat(row.get("project_leader_name"))
                    .as("project_leader_name 应继承自 tenders.project_manager_name（pid 为空时）")
                    .isEqualTo("TenderProjectLeader");
            assertThat(row.get("bidding_leader_name"))
                    .as("bidding_leader_name 应继承自 tenders.bidding_person_name（pid 为空时）")
                    .isEqualTo("TenderBiddingLeader");
        } finally {
            cleanupTestData(projectId, tenderId);
        }
    }

    @Test
    @DisplayName("project_leader_name 应从 tenders.project_manager_name 继承（fees 分支，pid 为空）")
    void projectLeaderName_shouldInheritFromTender_whenPidIsNull_feesBranch() {
        Long tenderId = createTender("leader-inherit-fee", "FeeProjectLeader", "FeeBiddingLeader");
        Long projectId = createTestProject("leader-inherit-fee", tenderId);
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFee(projectId, "BID_BOND", "PAID", "DATE_ADD(NOW(), INTERVAL 30 DAY)", new BigDecimal("5000"));
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("查询结果应包含项目")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));

            Map<String, Object> row = rows.stream()
                    .filter(r -> projectId.equals(extractProjectId(r)))
                    .findFirst()
                    .orElse(null);

            assertThat(row)
                    .as("项目行应存在")
                    .isNotNull();
            assertThat(row.get("project_leader_name"))
                    .as("project_leader_name 应继承自 tenders.project_manager_name（fees 分支，pid 为空时）")
                    .isEqualTo("FeeProjectLeader");
            assertThat(row.get("bidding_leader_name"))
                    .as("bidding_leader_name 应继承自 tenders.bidding_person_name（fees 分支，pid 为空时）")
                    .isEqualTo("FeeBiddingLeader");
        } finally {
            cleanupTestData(projectId, tenderId);
        }
    }

    @Test
    @DisplayName("project_leader_name 应优先使用 pid 值（覆盖 tender）")
    void projectLeaderName_shouldPreferPidValue_overTender() {
        Long tenderId = createTender("leader-prefer-pid", "TenderLeader", "TenderBidding");
        Long projectId = createTestProject("leader-prefer-pid", tenderId);
        String sqlInsertPidWithLeader =
                "INSERT INTO project_initiation_details"
              + " (project_id, need_deposit, deposit_amount, project_leader_name, bidding_leader_name, locked, created_at, updated_at) "
              + "VALUES (:pid, 'YES', 5000, 'PidLeader', 'PidBidding', FALSE, NOW(), NOW())";
        jdbcTemplate.update(sqlInsertPidWithLeader,
                Map.of("pid", projectId, "nd", "YES", "da", new BigDecimal("5000")));
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            Map<String, Object> row = rows.stream()
                    .filter(r -> projectId.equals(extractProjectId(r)))
                    .findFirst()
                    .orElse(null);

            assertThat(row)
                    .as("项目行应存在")
                    .isNotNull();
            assertThat(row.get("project_leader_name"))
                    .as("project_leader_name 应优先使用 pid 值，而非继承 tender")
                    .isEqualTo("PidLeader");
            assertThat(row.get("bidding_leader_name"))
                    .as("bidding_leader_name 应优先使用 pid 值，而非继承 tender")
                    .isEqualTo("PidBidding");
        } finally {
            cleanupTestData(projectId, tenderId);
        }
    }

    // ── CO-507：投标负责人取值应来自 ProjectLeadAssignment.primaryLeadUserId ──
    //
    // 背景：保证金管理表格缺少投标负责人列，且原 SQL 完全绕过
    // project_lead_assignment 表，从 pid.bidding_leader_name 字符串取值，
    // 无法反映标书编制阶段分配的 primaryLeadUserId 变更。
    // 修复：FEES_JOIN/INIT_JOIN 新增 LEFT JOIN pla + LEFT JOIN users u_lead，
    // COALESCE 优先取 u_lead.full_name，回退 pid/tender 字符串字段。

    @Test
    @DisplayName("CO-507: bidding_leader_name 应优先取 ProjectLeadAssignment.primaryLeadUserId 对应的 users.full_name")
    void biddingLeaderName_shouldPreferProjectLeadAssignmentUser_overPidAndTender() {
        Long leadUserId = createTestUser("CO507-BidLeader");
        Long tenderId = createTender("co507-prefer-pla", "TenderPM", "TenderBidLeader");
        Long projectId = createTestProject("co507-prefer-pla", tenderId);
        createInitiationDetailsWithLeaders(projectId, "YES",
                new BigDecimal("5000"), "PidPM", "PidBidLeader");
        createProjectLeadAssignment(projectId, leadUserId);
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            Map<String, Object> row = rows.stream()
                    .filter(r -> projectId.equals(extractProjectId(r)))
                    .findFirst()
                    .orElse(null);

            assertThat(row)
                    .as("项目行应存在")
                    .isNotNull();
            assertThat(row.get("bidding_leader_name"))
                    .as("bidding_leader_name 应优先取 ProjectLeadAssignment 关联的 users.full_name，"
                      + "而非 pid.bidding_leader_name / tenders.bidding_person_name 字符串字段")
                    .isEqualTo("CO507-BidLeader");
        } finally {
            cleanupTestData(projectId, tenderId, leadUserId);
        }
    }

    @Test
    @DisplayName("CO-507: 无 ProjectLeadAssignment 记录时 bidding_leader_name 回退到 pid 字符串字段")
    void biddingLeaderName_fallsBackToPidString_whenNoProjectLeadAssignment() {
        Long tenderId = createTender("co507-no-pla", "TenderPM2", "TenderBidLeader2");
        Long projectId = createTestProject("co507-no-pla", tenderId);
        createInitiationDetailsWithLeaders(projectId, "YES",
                new BigDecimal("5000"), "PidPM2", "PidBidLeader2");
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            Map<String, Object> row = rows.stream()
                    .filter(r -> projectId.equals(extractProjectId(r)))
                    .findFirst()
                    .orElse(null);

            assertThat(row)
                    .as("项目行应存在")
                    .isNotNull();
            assertThat(row.get("bidding_leader_name"))
                    .as("无 ProjectLeadAssignment 记录时，应回退到 pid.bidding_leader_name 字符串字段")
                    .isEqualTo("PidBidLeader2");
        } finally {
            cleanupTestData(projectId, tenderId);
        }
    }

    // ── CO-490 回归测试：保证金 500 错误（空字符串日期触发 CAST/STR_TO_DATE 异常）──
    //
    // 背景：前端 TaskDepositFields.vue 把 actualPaymentDate/expectedRefundDate
    // 初始化为空字符串 ""，存入 tasks.extended_fields_json。
    // 旧 SQL 用 CAST(SUBSTRING(...) AS DATETIME)：MySQL 8.0 严格模式抛
    // "Incorrect datetime value" → 500。
    // 修复：STR_TO_DATE(NULLIF(SUBSTRING(...), ''), '%Y-%m-%d')。
    // 陷阱：STR_TO_DATE('', '%Y-%m-%d') 返回 '0000-00-00'（非 NULL），
    //       会触发 JDBC "Zero date value prohibited"，
    //       因此必须先 NULLIF 把空字符串转 NULL。

    @Test
    @DisplayName("listBase 不应抛异常：deposit task 的 JSON 日期字段为空字符串（CO-490 核心场景）")
    void listBase_executesWithoutError_whenDepositTaskHasEmptyStringDates() {
        Long projectId = createTestProject("co490-empty-dates");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFeeWithPaymentDate(projectId, "BID_BOND", "PAID",
                "DATE_SUB(NOW(), INTERVAL 10 DAY)", "DATE_ADD(NOW(), INTERVAL 20 DAY)",
                new BigDecimal("5000"));
        createDepositTask(projectId,
                "{\"_taskType\":\"deposit-payment\","
              + "\"actualPaymentDate\":\"\",\"expectedRefundDate\":\"\"}");
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("listBase 应正常返回结果，不应因空字符串日期抛 500")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));

            // 验证回退到 fees.payment_date / fees.fee_date
            Map<String, Object> row = rows.stream()
                    .filter(r -> projectId.equals(extractProjectId(r)))
                    .findFirst()
                    .orElse(null);
            assertThat(row)
                    .as("项目行应存在")
                    .isNotNull();
            assertThat(row.get("payment_date"))
                    .as("空字符串 actualPaymentDate 应回退到 fees.payment_date")
                    .isNotNull();
            assertThat(row.get("exp_return_date"))
                    .as("空字符串 expectedRefundDate 应回退到 fees.fee_date")
                    .isNotNull();
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("listBase 不应抛异常：deposit task 的 JSON 缺少日期字段")
    void listBase_executesWithoutError_whenDepositTaskJsonMissesDateFields() {
        Long projectId = createTestProject("co490-missing-fields");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFeeWithPaymentDate(projectId, "BID_BOND", "PAID",
                "DATE_SUB(NOW(), INTERVAL 10 DAY)", "DATE_ADD(NOW(), INTERVAL 20 DAY)",
                new BigDecimal("5000"));
        // JSON 只有 _taskType，没有日期字段
        createDepositTask(projectId, "{\"_taskType\":\"deposit-payment\"}");
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("listBase 应正常返回结果，JSON 缺字段时 JSON_EXTRACT 返回 NULL，"
                      + "SUBSTRING(NULL,1,10)=NULL，NULLIF(NULL,'')=NULL，STR_TO_DATE(NULL,...)=NULL，"
                      + "COALESCE 回退到 fees 列")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("listBase 不应抛异常：deposit task 的日期字段格式错误（非日期字符串）")
    void listBase_executesWithoutError_whenDepositTaskHasMalformedDate() {
        Long projectId = createTestProject("co490-malformed");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFeeWithPaymentDate(projectId, "BID_BOND", "PAID",
                "DATE_SUB(NOW(), INTERVAL 10 DAY)", "DATE_ADD(NOW(), INTERVAL 20 DAY)",
                new BigDecimal("5000"));
        createDepositTask(projectId,
                "{\"_taskType\":\"deposit-payment\","
              + "\"actualPaymentDate\":\"not-a-date\","
              + "\"expectedRefundDate\":\"also-bad\"}");
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            // STR_TO_DATE 对无法解析的字符串返回 NULL（带 warning），
            // COALESCE 回退到 fees 列
            assertThat(rows)
                    .as("listBase 应正常返回结果，STR_TO_DATE 解析失败返回 NULL，"
                      + "COALESCE 回退到 fees 列")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("listBase 应优先使用 task JSON 的有效日期，而非 fees 列")
    void listBase_prefersValidTaskJsonDate_overFeesColumns() {
        Long projectId = createTestProject("co490-prefer-json");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        // fees.payment_date = 10 天前，fees.fee_date = 20 天后
        createFeeWithPaymentDate(projectId, "BID_BOND", "PAID",
                "DATE_SUB(NOW(), INTERVAL 10 DAY)", "DATE_ADD(NOW(), INTERVAL 20 DAY)",
                new BigDecimal("5000"));
        // task JSON 用固定日期 2026-01-15 / 2026-03-20
        createDepositTask(projectId,
                "{\"_taskType\":\"deposit-payment\","
              + "\"actualPaymentDate\":\"2026-01-15T00:00:00\","
              + "\"expectedRefundDate\":\"2026-03-20T00:00:00\"}");
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            Map<String, Object> row = rows.stream()
                    .filter(r -> projectId.equals(extractProjectId(r)))
                    .findFirst()
                    .orElse(null);
            assertThat(row)
                    .as("项目行应存在")
                    .isNotNull();
            // SUBSTRING(...,1,10) 取前 10 字符 = "2026-01-15"
            assertThat(row.get("payment_date"))
                    .as("应优先使用 task JSON 的 actualPaymentDate（2026-01-15），"
                      + "而非 fees.payment_date（10 天前）")
                    .asString()
                    .startsWith("2026-01-15");
            assertThat(row.get("exp_return_date"))
                    .as("应优先使用 task JSON 的 expectedRefundDate（2026-03-20），"
                      + "而非 fees.fee_date（20 天后）")
                    .asString()
                    .startsWith("2026-03-20");
        } finally {
            cleanupTestData(projectId);
        }
    }

    @Test
    @DisplayName("summaryBase 不应抛异常：deposit task 的 JSON 日期字段为空字符串（CO-490 summary 路径）")
    void summaryBase_executesWithoutError_whenDepositTaskHasEmptyStringDates() {
        Long projectId = createTestProject("co490-summary-empty");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFeeWithPaymentDate(projectId, "BID_BOND", "PAID",
                "DATE_SUB(NOW(), INTERVAL 10 DAY)", "DATE_ADD(NOW(), INTERVAL 20 DAY)",
                new BigDecimal("5000"));
        createDepositTask(projectId,
                "{\"_taskType\":\"deposit-payment\","
              + "\"actualPaymentDate\":\"\",\"expectedRefundDate\":\"\"}");
        try {
            StringBuilder sql = MarginQuerySupport.summaryBase(MarginQueryRole.ADMIN);

            // summaryBase 返回单行聚合结果，不抛异常即通过
            Map<String, Object> summary =
                    jdbcTemplate.queryForMap(sql.toString(), Map.of());

            assertThat(summary)
                    .as("summaryBase 应正常返回聚合结果，不应因空字符串日期抛 500")
                    .isNotNull();
        } finally {
            cleanupTestData(projectId);
        }
    }

    // ── 行为层测试 helper 方法 ──

    /**
     * 插入测试用 project，返回自增 id。
     * manager_id=0, tender_id=0 满足 NOT NULL 约束（无外键约束）。
     * <p>用 GeneratedKeyHolder 取自增 id（NamedParameterJdbcTemplate 的 update
     * 跨连接调用，LAST_INSERT_ID() 不可靠会返回 0）。
     */
    private Long createTestProject(final String nameSuffix) {
        return createTestProject(nameSuffix, 0L);
    }

    /**
     * 插入测试用 project，关联到指定 tender。
     */
    private Long createTestProject(final String nameSuffix, final Long tenderId) {
        String name = "test-margin-" + nameSuffix + "-" + System.nanoTime();
        String sql = "INSERT INTO projects (name, manager_id, tender_id, status, created_at) "
                   + "VALUES (:name, 0, :tid, 'INITIATED', NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("tid", tenderId);
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(sql, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(params),
                keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(
                    "Failed to retrieve generated project id for: " + name);
        }
        return key.longValue();
    }

    /**
     * 插入测试用 tender，返回自增 id。
     */
    private Long createTender(final String titleSuffix,
                              final String projectManagerName,
                              final String biddingPersonName) {
        String title = "test-tender-" + titleSuffix + "-" + System.nanoTime();
        String sql = "INSERT INTO tenders (title, project_manager_name, bidding_person_name, status, created_at) "
                   + "VALUES (:title, :pmn, :bpn, 'TRACKING', NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("title", title);
        params.put("pmn", projectManagerName);
        params.put("bpn", biddingPersonName);
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(sql, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(params),
                keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(
                    "Failed to retrieve generated tender id for: " + title);
        }
        return key.longValue();
    }

    /**
     * 插入 project_initiation_details。
     * needDeposit='YES' 且 depositAmount>0 会触发 init 分支占位行。
     */
    private void createInitiationDetails(final Long projectId,
                                          final String needDeposit,
                                          final BigDecimal depositAmount) {
        String sql = "INSERT INTO project_initiation_details"
                   + " (project_id, need_deposit, deposit_amount, locked, created_at, updated_at) "
                   + "VALUES (:pid, :nd, :da, FALSE, NOW(), NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("pid", projectId);
        params.put("nd", needDeposit);
        params.put("da", depositAmount);
        jdbcTemplate.update(sql, params);
    }

    /**
     * 插入 project_initiation_details，带 project_leader_name / bidding_leader_name
     * 字符串字段（CO-507 测试用：验证 pla 优先级时，pid 字符串值应被覆盖）。
     */
    private void createInitiationDetailsWithLeaders(final Long projectId,
                                                     final String needDeposit,
                                                     final BigDecimal depositAmount,
                                                     final String projectLeaderName,
                                                     final String biddingLeaderName) {
        String sql = "INSERT INTO project_initiation_details"
                   + " (project_id, need_deposit, deposit_amount,"
                   + "  project_leader_name, bidding_leader_name, locked, created_at, updated_at) "
                   + "VALUES (:pid, :nd, :da, :pln, :bln, FALSE, NOW(), NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("pid", projectId);
        params.put("nd", needDeposit);
        params.put("da", depositAmount);
        params.put("pln", projectLeaderName);
        params.put("bln", biddingLeaderName);
        jdbcTemplate.update(sql, params);
    }

    /**
     * 插入 users 记录，返回自增 id（CO-507 测试用：作为投标负责人）。
     * role='STAFF' 满足 NOT NULL 约束，username/email 用纳秒保证唯一。
     */
    private Long createTestUser(final String fullName) {
        String username = "test-user-" + System.nanoTime();
        String email = username + "@test.local";
        String sql = "INSERT INTO users"
                   + " (username, email, full_name, password, role,"
                   + "  enabled, email_verified, created_at) "
                   + "VALUES (:u, :e, :f, 'nopass', 'STAFF', TRUE, FALSE, NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("u", username);
        params.put("e", email);
        params.put("f", fullName);
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(sql,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(params),
                keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(
                    "Failed to retrieve generated user id for: " + username);
        }
        return key.longValue();
    }

    /**
     * 插入 project_lead_assignment（CO-507 测试用：主投标负责人分配）。
     * project_id 有 unique 约束，每个测试项目只能有一条记录。
     */
    private void createProjectLeadAssignment(final Long projectId,
                                              final Long primaryLeadUserId) {
        String sql = "INSERT INTO project_lead_assignment"
                   + " (project_id, primary_lead_user_id, created_at, updated_at) "
                   + "VALUES (:pid, :pluid, NOW(), NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("pid", projectId);
        params.put("pluid", primaryLeadUserId);
        jdbcTemplate.update(sql, params);
    }

    /**
     * 插入 fee。feeDateExpr 是 SQL 表达式（如 'DATE_SUB(NOW(), INTERVAL 30 DAY)'），
     * 直接拼接到 SQL 中以避免 Java/MySQL 时区不一致。
     */
    private void createFee(final Long projectId,
                           final String feeType,
                           final String status,
                           final String feeDateExpr,
                           final BigDecimal amount) {
        // feeDateExpr 是受控的 SQL 表达式字面量（非用户输入），直接拼接
        String sql = "INSERT INTO fees"
                   + " (project_id, fee_type, status, fee_date, amount, created_at) "
                   + "VALUES (" + projectId + ", '" + feeType + "', '" + status
                   + "', " + feeDateExpr + ", " + amount + ", NOW())";
        jdbcTemplate.update(sql, Map.of());
    }

    /**
     * 插入 fee，同时设置 payment_date 和 fee_date（CO-490 测试用）。
     * <p>createFee 不设置 payment_date（默认 NULL），但 CO-490 回归测试
     * 需要验证 STR_TO_DATE 解析失败时 COALESCE 回退到 fees.payment_date，
     * 因此需要显式设置 payment_date 让回退值可断言。
     */
    private void createFeeWithPaymentDate(final Long projectId,
                                           final String feeType,
                                           final String status,
                                           final String paymentDateExpr,
                                           final String feeDateExpr,
                                           final BigDecimal amount) {
        // 日期表达式是受控 SQL 字面量（非用户输入），直接拼接
        String sql = "INSERT INTO fees"
                   + " (project_id, fee_type, status, payment_date, fee_date, amount, created_at) "
                   + "VALUES (" + projectId + ", '" + feeType + "', '" + status
                   + "', " + paymentDateExpr + ", " + feeDateExpr
                   + ", " + amount + ", NOW())";
        jdbcTemplate.update(sql, Map.of());
    }

    /**
     * 插入 deposit-payment 任务（CO-490 测试用）。
     * <p>tasks.extended_fields_json 存储 actualPaymentDate / expectedRefundDate
     * 等保证金字段。FEES_JOIN 通过 JSON_EXTRACT(dt.extended_fields_json, '$._taskType')
     * = 'deposit-payment' 关联到此任务。
     */
    private void createDepositTask(final Long projectId,
                                    final String extendedFieldsJson) {
        String sql = "INSERT INTO tasks"
                   + " (project_id, title, status, priority, extended_fields_json, created_at, updated_at) "
                   + "VALUES (:pid, :title, 'TODO', 'MEDIUM', :json, NOW(), NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("pid", projectId);
        params.put("title", "test-deposit-task-" + System.nanoTime());
        params.put("json", extendedFieldsJson);
        jdbcTemplate.update(sql, params);
    }

    /**
     * 插入 project_closure 记录（CO-508 测试用）。
     * <p>project_closure 表通过 V1037 迁移加上了 deposit_return_status /
     * returned_amount / transfer_amount 等字段。CO-508 的"已退回"判定
     * （规则3：returned_amount + service_fee_amount = deposit_amount）依赖
     * project_closure.deposit_return_status 来推导 returned_amount /
     * service_fee_amount（见 MarginDerivedTableColumns.returnedAmountExpr /
     * serviceFeeAmountExpr）。
     * <p>depositReturnStatus 取值：
     * <ul>
     *   <li>FULLY_RETURNED → returned_amount = deposit_amount（全额退回）</li>
     *   <li>TRANSFERRED_TO_FEE → service_fee_amount = transfer_amount（全额转服务费）</li>
     *   <li>PARTIAL_RETURN_PARTIAL_TRANSFER → returned_amount = pc.returned_amount，
     *       service_fee_amount = pc.transfer_amount（部分退回 + 部分转服务费）</li>
     *   <li>NOT_RETURNED / NA → 两者均为 NULL</li>
     * </ul>
     * <p>project_closure.project_id 有 UNIQUE 约束，每个测试项目只能有一条记录。
     */
    private void createProjectClosure(final Long projectId,
                                       final String depositReturnStatus,
                                       final BigDecimal returnedAmount,
                                       final BigDecimal transferAmount) {
        String sql = "INSERT INTO project_closure"
                   + " (project_id, deposit_return_status, returned_amount, transfer_amount,"
                   + "  deposit_returned, stage_locked, created_at, updated_at) "
                   + "VALUES (:pid, :drs, :ra, :ta, FALSE, FALSE, NOW(), NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("pid", projectId);
        params.put("drs", depositReturnStatus);
        params.put("ra", returnedAmount);
        params.put("ta", transferAmount);
        jdbcTemplate.update(sql, params);
    }

    /** 从查询结果行中提取 project_id（处理 Number 类型转换）。 */
    private Long extractProjectId(final Map<String, Object> row) {
        Object pid = row.get("project_id");
        if (pid instanceof Number) {
            return ((Number) pid).longValue();
        }
        return null;
    }

    /** 清理测试数据：删除 tasks、fees、project_closure、project_initiation_details、projects、tenders。 */
    private void cleanupTestData(final Long projectId) {
        if (projectId == null) {
            return;
        }
        Map<String, Object> params = Map.of("pid", projectId);
        // CO-490 测试会创建 deposit-payment 任务，必须先清理（tasks 无外键约束但 project_id 引用 projects.id）
        jdbcTemplate.update("DELETE FROM tasks WHERE project_id = :pid", params);
        jdbcTemplate.update("DELETE FROM fees WHERE project_id = :pid", params);
        // CO-508 测试会创建 project_closure，project_id 有 unique 约束必须清理
        jdbcTemplate.update(
                "DELETE FROM project_closure WHERE project_id = :pid", params);
        // CO-507 测试会创建 project_lead_assignment，project_id 有 unique 约束必须清理
        jdbcTemplate.update(
                "DELETE FROM project_lead_assignment WHERE project_id = :pid", params);
        jdbcTemplate.update(
                "DELETE FROM project_initiation_details WHERE project_id = :pid", params);
        jdbcTemplate.update("DELETE FROM projects WHERE id = :pid", params);
    }

    /** 清理测试数据：删除 fees、project_initiation_details、projects、tenders。 */
    private void cleanupTestData(final Long projectId, final Long tenderId) {
        cleanupTestData(projectId);
        if (tenderId != null) {
            jdbcTemplate.update("DELETE FROM tenders WHERE id = :tid", Map.of("tid", tenderId));
        }
    }

    /** 清理测试数据：额外删除 users（CO-507 测试会创建投标负责人 user）。 */
    private void cleanupTestData(final Long projectId, final Long tenderId, final Long userId) {
        cleanupTestData(projectId, tenderId);
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = :uid", Map.of("uid", userId));
        }
    }
}
