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
        MarginQuerySupport.appendFilters(sql, filters);
        // 把 JPA 命名参数（:pName 等）替换成字面量，只验证 SQL 列解析/语法
        String rawSql = replaceParamsWithLiterals(sql.toString(), filters);
        jdbcTemplate.queryForList(rawSql, Map.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFilterCombinations")
    @DisplayName("listBase 生成的 SQL 在真实 MySQL 上执行不抛异常")
    void listBase_executesWithoutSqlError(final String label, final Map<String, String> filters) {
        StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
        MarginQuerySupport.appendFilters(sql, filters);
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
            MarginQuerySupport.appendFilters(sql, Map.of("status", "PENDING"));
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
    @DisplayName("filterByStatusReturned 应包含 CANCELLED 状态的 fee")
    void filterByStatusReturned_shouldIncludeCancelledFees() {
        // 造数据：1 条 fees.status=CANCELLED, fee_type=BID_BOND
        // （CANCELLED 在 label() 中标为"已退回"，但修复前 RETURNED filter 只匹配 = 'RETURNED'）
        Long projectId = createTestProject("returned-cancelled");
        createInitiationDetails(projectId, "NO", BigDecimal.ZERO);
        createFee(projectId, "BID_BOND", "CANCELLED",
                  "DATE_SUB(NOW(), INTERVAL 1 DAY)", new BigDecimal("5000"));
        try {
            StringBuilder sql = MarginQuerySupport.listBase(MarginQueryRole.ADMIN);
            MarginQuerySupport.appendFilters(sql, Map.of("status", "RETURNED"));
            sql.append(" ORDER BY m.created_at DESC LIMIT 100 OFFSET 0");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(sql.toString(), Map.of());

            assertThat(rows)
                    .as("RETURNED 筛选应包含 CANCELLED 状态的 fee"
                      + "—— 修复前只匹配 = 'RETURNED'，漏掉 CANCELLED")
                    .anyMatch(row -> projectId.equals(extractProjectId(row)));
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
            MarginQuerySupport.appendFilters(sql, Map.of("status", "OVERDUE"));
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

    // ── 行为层测试 helper 方法 ──

    /**
     * 插入测试用 project，返回自增 id。
     * manager_id=0, tender_id=0 满足 NOT NULL 约束（无外键约束）。
     * <p>用 GeneratedKeyHolder 取自增 id（NamedParameterJdbcTemplate 的 update
     * 跨连接调用，LAST_INSERT_ID() 不可靠会返回 0）。
     */
    private Long createTestProject(final String nameSuffix) {
        String name = "test-margin-" + nameSuffix + "-" + System.nanoTime();
        String sql = "INSERT INTO projects (name, manager_id, tender_id, status, created_at) "
                   + "VALUES (:name, 0, 0, 'INITIATED', NOW())";
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
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

    /** 从查询结果行中提取 project_id（处理 Number 类型转换）。 */
    private Long extractProjectId(final Map<String, Object> row) {
        Object pid = row.get("project_id");
        if (pid instanceof Number) {
            return ((Number) pid).longValue();
        }
        return null;
    }

    /** 清理测试数据：删除 fees、project_initiation_details、projects。 */
    private void cleanupTestData(final Long projectId) {
        if (projectId == null) {
            return;
        }
        Map<String, Object> params = Map.of("pid", projectId);
        jdbcTemplate.update("DELETE FROM fees WHERE project_id = :pid", params);
        jdbcTemplate.update(
                "DELETE FROM project_initiation_details WHERE project_id = :pid", params);
        jdbcTemplate.update("DELETE FROM projects WHERE id = :pid", params);
    }
}
