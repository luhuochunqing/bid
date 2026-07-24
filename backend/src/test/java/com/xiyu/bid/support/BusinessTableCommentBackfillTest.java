package com.xiyu.bid.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1177 迁移脚本 COMMENT 补全验证。
 *
 * <p>验证范围:P0 阶段 5 张核心业务表(accounts、users、tenders、projects、tasks)
 * 的表级 COMMENT 和关键字段 COMMENT 已正确写入 MySQL information_schema。
 *
 * <p>测试策略:抽样断言核心字段,不要求全字段覆盖(避免测试文件过长)。
 * 全字段覆盖由 {@code npm run db:generate-schema} 生成的 db-schema.md 文档可视化验收。
 *
 * <p>ENUM 类型保护:测试断言 tenders.status/users.role/projects.status/tasks.status
 * 的 COLUMN_TYPE,确保 V1177 的 MODIFY COLUMN 没有回滚 B73 之后迁移(V117/V1052/V1091)的 ENUM 扩展。
 * 采用包含性断言而非精确匹配,以适应未来可能的 ENUM 值扩展。
 *
 * <p>后续 P1/P2 阶段补全其他表后,可在本测试类追加断言。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("flyway-mysql")
@Testcontainers(disabledWithoutDocker = true)
@Import(NoOpPasswordEncryptionTestConfig.class)
class BusinessTableCommentBackfillTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("xiyu_bid_test")
            .withUsername("xiyu")
            .withPassword("xiyu");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    // ── 表级 COMMENT 断言 ──

    @Test
    void accountsTableCommentBackfilled() {
        assertEquals("客户/供应商/合作伙伴账户表", tableComment("accounts"));
    }

    @Test
    void usersTableCommentBackfilled() {
        assertEquals("系统用户表(本地账户,OSS 同步用户也写入此表)", tableComment("users"));
    }

    @Test
    void tendersTableCommentBackfilled() {
        assertEquals(
                "标讯信息表(来源:外部平台抓取/CRM商机推送/人工录入/批量导入)",
                tableComment("tenders")
        );
    }

    @Test
    void projectsTableCommentBackfilled() {
        assertEquals("投标项目表(标讯转化后的投标业务实体)", tableComment("projects"));
    }

    @Test
    void tasksTableCommentBackfilled() {
        assertEquals("项目任务表(投标项目下的具体任务)", tableComment("tasks"));
    }

    // ── accounts 关键字段 COMMENT 断言 ──

    @Test
    void accountsKeyColumnCommentsBackfilled() {
        assertEquals("主键ID", columnComment("accounts", "id"));
        assertEquals("账户名称(唯一)", columnComment("accounts", "name"));
        assertEquals("信用等级:A/B/C/D", columnComment("accounts", "credit_level"));
        assertEquals(
                "账户类型:CLIENT客户/SUPPLIER供应商/PARTNER合作伙伴/GOVERNMENT政府/OTHER其他",
                columnComment("accounts", "type")
        );
    }

    // ── users 关键字段 COMMENT 断言 ──

    @Test
    void usersKeyColumnCommentsBackfilled() {
        assertEquals("登录用户名(唯一)", columnComment("users", "username"));
        assertEquals("BCrypt加密密码", columnComment("users", "password"));
        assertEquals("邮箱地址(唯一)", columnComment("users", "email"));
        assertEquals("用户真实姓名", columnComment("users", "full_name"));
        assertEquals(
                "历史角色(已弃用,权限以 role_id 为准)",
                columnComment("users", "role")
        );
        assertEquals("关联角色ID(role_profile)", columnComment("users", "role_id"));
        assertEquals("是否启用:1是 0否", columnComment("users", "enabled"));
    }

    // ── tenders 关键字段 COMMENT 断言 ──

    @Test
    void tendersKeyColumnCommentsBackfilled() {
        assertEquals("标讯标题", columnComment("tenders", "title"));
        assertEquals("标讯来源平台(原始字符串)", columnComment("tenders", "source"));
        assertEquals(
                "标讯状态:待分配/跟踪中/已评估/投标中/中标/未中标/已放弃",
                columnComment("tenders", "status")
        );
        assertEquals("AI评分(0-100)", columnComment("tenders", "ai_score"));
        assertEquals("投标截止时间", columnComment("tenders", "deadline"));
        assertEquals(
                "标讯来源类型:外部平台/CRM商机/人工录入/批量导入",
                columnComment("tenders", "source_type")
        );
    }

    // ── projects 关键字段 COMMENT 断言 ──

    @Test
    void projectsKeyColumnCommentsBackfilled() {
        assertEquals("项目名称", columnComment("projects", "name"));
        assertEquals(
                "项目状态:待立项/已立项/投标中/评标中/中标/未中标/失败/已放弃",
                columnComment("projects", "status")
        );
        assertEquals("关联标讯ID", columnComment("projects", "tender_id"));
        assertEquals("项目经理ID(关联 users.id)", columnComment("projects", "manager_id"));
    }

    // ── tasks 关键字段 COMMENT 断言 ──

    @Test
    void tasksKeyColumnCommentsBackfilled() {
        assertEquals("任务标题", columnComment("tasks", "title"));
        assertEquals("关联项目ID", columnComment("tasks", "project_id"));
        assertEquals(
                "任务状态(常见值:TODO/IN_PROGRESS/REVIEW/COMPLETED/CANCELLED)",
                columnComment("tasks", "status")
        );
        assertEquals(
                "优先级:LOW低/MEDIUM中/HIGH高/URGENT紧急",
                columnComment("tasks", "priority")
        );
    }

    // ── ENUM 类型保护断言(防止 V1177 回滚 B73 之后迁移的 ENUM 扩展) ──

    @Test
    void tendersStatusEnumNotRolledBack() {
        String statusType = columnType("tenders", "status");
        assertTrue(statusType.startsWith("enum("), "tenders.status 应为 ENUM 类型");
        assertTrue(statusType.contains("PENDING_ASSIGNMENT"), "应包含待分配状态(V117 新增)");
        assertTrue(statusType.contains("TRACKING"), "应包含跟踪中状态");
        assertTrue(statusType.contains("EVALUATED"), "应包含已评估状态");
        assertTrue(statusType.contains("BIDDING"), "应包含投标中状态");
        assertTrue(statusType.contains("WON"), "应包含中标状态");
        assertTrue(statusType.contains("LOST"), "应包含未中标状态");
        assertTrue(statusType.contains("ABANDONED"), "应包含已放弃状态(V117 新增)");
    }

    @Test
    void projectsStatusEnumNotRolledBack() {
        String statusType = columnType("projects", "status");
        assertTrue(statusType.startsWith("enum("), "projects.status 应为 ENUM 类型");
        assertTrue(statusType.contains("PENDING_INITIATION"), "应包含待立项状态(V1052 新增)");
        assertTrue(statusType.contains("INITIATED"), "应包含已立项状态");
        assertTrue(statusType.contains("BIDDING"), "应包含投标中状态");
        assertTrue(statusType.contains("EVALUATING"), "应包含评标中状态(V1052 新增)");
        assertTrue(statusType.contains("WON"), "应包含中标状态");
        assertTrue(statusType.contains("LOST"), "应包含未中标状态");
        assertTrue(statusType.contains("FAILED"), "应包含失败状态(V1052 新增)");
        assertTrue(statusType.contains("ABANDONED"), "应包含已放弃状态(V1052 新增)");
    }

    @Test
    void usersRoleEnumNotRolledBack() {
        String roleType = columnType("users", "role");
        assertTrue(roleType.startsWith("enum("), "users.role 应为 ENUM 类型");
        assertTrue(roleType.contains("ADMIN"), "应包含 ADMIN");
        assertTrue(roleType.contains("MANAGER"), "应包含 MANAGER");
        assertFalse(roleType.contains("STAFF"), "不应包含 STAFF(V1091 已移除)");
    }

    @Test
    void tasksStatusRemainsVarchar() {
        assertEquals("varchar(32)", columnType("tasks", "status"));
    }

    // ── 已有 COMMENT 未被覆盖断言 ──

    @Test
    void existingCommentsNotOverwritten() {
        assertEquals("公告正文", columnComment("tenders", "bid_notice"));
        assertEquals("报名截止时间", columnComment("tenders", "registration_deadline"));
        assertEquals("乐观锁版本号", columnComment("tenders", "version"));
        assertEquals("立项时间（首次进入INITIATED阶段）", columnComment("projects", "initiated_at"));
        assertEquals("任务详细描述（Markdown 文本，上限 64KB）", columnComment("tasks", "content"));
    }

    // ── V1177 迁移脚本执行成功断言 ──

    @Test
    void v1177MigrationAppliedSuccessfully() {
        Integer v1177SuccessCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from flyway_schema_history
                where success = 1
                  and version = '1177'
                  and script = 'V1177__backfill_business_table_comments.sql'
                """,
                Integer.class
        );
        assertEquals(1, v1177SuccessCount, "V1177 迁移脚本应成功执行");
    }

    // ── 辅助方法 ──

    private String tableComment(String tableName) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                select table_comment
                from information_schema.tables
                where table_schema = database()
                  and table_name = ?
                """,
                tableName
        );
        Object comment = row.get("table_comment");
        assertNotNull(comment, "表 " + tableName + " 的 table_comment 不应为 null");
        return comment.toString();
    }

    private String columnComment(String tableName, String columnName) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                select column_comment
                from information_schema.columns
                where table_schema = database()
                  and table_name = ?
                  and column_name = ?
                """,
                tableName, columnName
        );
        Object comment = row.get("column_comment");
        assertNotNull(comment, "表 " + tableName + "." + columnName + " 的 column_comment 不应为 null");
        String value = comment.toString();
        assertFalse(value.isBlank(), "表 " + tableName + "." + columnName + " 的 column_comment 不应为空");
        return value;
    }

    private String columnType(String tableName, String columnName) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                select column_type
                from information_schema.columns
                where table_schema = database()
                  and table_name = ?
                  and column_name = ?
                """,
                tableName, columnName
        );
        Object type = row.get("column_type");
        assertNotNull(type, "表 " + tableName + "." + columnName + " 的 column_type 不应为 null");
        return type.toString();
    }
}
