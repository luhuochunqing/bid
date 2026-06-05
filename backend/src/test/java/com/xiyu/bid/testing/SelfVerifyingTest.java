package com.xiyu.bid.testing;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.Objects;

/**
 * 自验证测试基类
 *
 * 提供跨层验证能力，确保：
 * - API响应 ↔ 数据库状态 一致性
 * - 审计日志正确记录
 * - 状态转换符合状态机规则
 *
 * 继承此类自动获得自验证能力。
 *
 * 使用示例:
 * <pre>
 * &#64;ExtendWith(SpringExtension.class)
 * class CalendarServiceTest extends SelfVerifyingTest {
 *
 *     &#64;Autowired
 *     private CalendarService calendarService;
 *
 *     &#64;Test
 *     void createEvent_shouldPersistAndAudit() {
 *         // Given
 *         CalendarEventCreateRequest request = newRequest();
 *
 *         // When
 *         CalendarEventDTO result = calendarService.createEvent(request);
 *
 *         // Then - 自动验证三层一致性
 *         shadowVerify("calendar_events", result.getId())
 *             .exists()
 *             .hasAuditLog()
 *             .timestampsValid(false);
 *     }
 * }
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class SelfVerifyingTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ShadowInspector shadowInspector;

    @PersistenceContext
    protected EntityManager entityManager;

    /**
     * 开始三层一致性验证
     *
     * @param tableName 数据库表名
     * @param entityId 实体ID
     * @return 验证链
     */
    protected ShadowInspector.VerificationChain shadowVerify(String tableName, Long entityId) {
        flushPersistenceContext();
        return shadowInspector.verify(tableName, entityId);
    }

    /**
     * 直接查询数据库
     *
     * @param sql SQL查询
     * @param args 参数
     * @return 查询结果
     */
    protected Map<String, Object> queryDatabase(String sql, Object... args) {
        flushPersistenceContext();
        return jdbcTemplate.queryForMap(sql, args);
    }

    /**
     * 查询单个值
     *
     * @param sql SQL查询
     * @param type 返回类型
     * @param args 参数
     * @return 查询结果
     */
    protected <T> T queryForObject(String sql, Class<T> type, Object... args) {
        flushPersistenceContext();
        return jdbcTemplate.queryForObject(sql, type, args);
    }

    /**
     * 计数查询 (仅支持简单条件，防止SQL注入)
     *
     * @param tableName 表名
     * @param whereCondition WHERE条件 (仅支持 "id = ?")
     * @param args 参数
     * @return 计数
     * @throws IllegalArgumentException 如果whereCondition不是 "id = ?"
     */
    protected Integer count(String tableName, String whereCondition, Object... args) {
        flushPersistenceContext();
        // 安全检查：只允许简单的id查询
        if (!"id = ?".equals(whereCondition) && !"project_id = ?".equals(whereCondition)) {
            throw new IllegalArgumentException(
                "Only simple conditions like 'id = ?' or 'project_id = ?' are allowed for security"
            );
        }
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s", tableName, whereCondition);
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    /**
     * 检查记录是否存在
     *
     * @param tableName 表名
     * @param idValue ID值
     * @return 是否存在
     */
    protected boolean exists(String tableName, Long idValue) {
        Integer count = count(tableName, "id = ?", idValue);
        return count != null && count > 0;
    }

    protected void flushPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * 清理测试数据
     * 子类可以覆盖此方法
     */
    @AfterEach
    protected void cleanup() {
        // 默认不做额外清理，@Transactional会自动回滚
    }

    /**
     * 等待异步操作完成
     * 用于测试@Async方法
     *
     * @param millis 等待毫秒数
     */
    protected void awaitAsync(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 验证断言并带详细错误信息
     *
     * @param condition 条件
     * @param message 错误信息
     */
    protected void assertInvariant(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Invariant violation: " + message);
        }
    }

    /**
     * 验证两个值相等，带详细错误信息
     *
     * @param expected 期望值
     * @param actual 实际值
     * @param context 上下文描述
     */
    protected void assertEqualTo(Object expected, Object actual, String context) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(String.format(
                "%s - expected: %s, actual: %s", context, expected, actual));
        }
    }

    /**
     * 验证不为null
     *
     * @param value 值
     * @param name 名称
     */
    protected void assertNotNull(Object value, String name) {
        if (value == null) {
            throw new AssertionError(name + " should not be null");
        }
    }
}
