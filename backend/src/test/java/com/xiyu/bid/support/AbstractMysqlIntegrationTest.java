package com.xiyu.bid.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL 集成测试基类：CI 用 Testcontainers，本地 fallback 到手动容器。
 *
 * 背景：Testcontainers 1.19.3/1.20.4 的 docker-java 客户端与 Docker Desktop 29.x
 * 不兼容（Docker API 返回空 JSON + Status 400）。CI 环境（GitHub Actions ubuntu-latest）
 * 用 Docker daemon 不受影响。本地开发时 fallback 到手动启动的 MySQL 容器。
 *
 * sql_mode 调整：MySQL 8.0 默认开启 NO_ZERO_DATE + NO_ZERO_IN_DATE，
 * 导致 V1077 迁移脚本中的 '0000-00-00 00:00:00' 字面量触发 Error 1292。
 * 生产已跑过 V1077 不能改 checksum，因此在测试环境调整 sql_mode 去掉这两项。
 *
 * 使用方式：
 * 1. 本地启动 MySQL 容器：docker run -d --name xiyu-mysql-test -p 13306:3306 \
 *    -e MYSQL_ROOT_PASSWORD=xiyu_test -e MYSQL_DATABASE=xiyu_bid_verify mysql:8.0
 * 2. 测试类继承此基类，加 @SpringBootTest + @ActiveProfiles("flyway-mysql")
 *
 * 注意：不用 @Testcontainers(disabledWithoutDocker=true)，Docker 不可用时必须 fail-fast。
 */
public abstract class AbstractMysqlIntegrationTest {

    /**
     * 测试环境 sql_mode：去掉 NO_ZERO_DATE 和 NO_ZERO_IN_DATE，
     * 保留其他严格模式项（ONLY_FULL_GROUP_BY / STRICT_TRANS_TABLES / ERROR_FOR_DIVISION_BY_ZERO / NO_ENGINE_SUBSTITUTION）。
     */
    private static final String TEST_SQL_MODE =
            "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION";

    private static final String LOCAL_JDBC_URL =
            "jdbc:mysql://localhost:13306/xiyu_bid_verify?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String LOCAL_USERNAME = "root";
    private static final String LOCAL_PASSWORD = "xiyu_test";

    private static final MySQLContainer<?> CI_CONTAINER;
    private static final String JDBC_URL;
    private static final String DB_USERNAME;
    private static final String DB_PASSWORD;

    static {
        if (isCiEnvironment()) {
            // CI 用 Testcontainers：通过 MySQL 启动参数对齐生产 sql_mode + collation
            // - sql_mode 去掉 NO_ZERO_DATE/NO_ZERO_IN_DATE（V1077 '0000-00-00' 字面量兼容）
            // - collation-server 对齐生产 utf8mb4_unicode_ci（V1092 临时表 JOIN 不再触发 collation 冲突）
            CI_CONTAINER = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("xiyu_bid_test")
                    .withUsername("xiyu")
                    .withPassword("xiyu")
                    .withCommand(
                            "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_unicode_ci",
                            "--sql-mode=" + TEST_SQL_MODE);
            CI_CONTAINER.start();
            JDBC_URL = CI_CONTAINER.getJdbcUrl();
            DB_USERNAME = CI_CONTAINER.getUsername();
            DB_PASSWORD = CI_CONTAINER.getPassword();
        } else {
            CI_CONTAINER = null;
            JDBC_URL = LOCAL_JDBC_URL;
            DB_USERNAME = LOCAL_USERNAME;
            DB_PASSWORD = LOCAL_PASSWORD;
            // 本地手动容器：通过 SET GLOBAL + ALTER DATABASE 对齐 CI 配置
            adjustLocalSessionSettings();
        }
    }

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> DB_USERNAME);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    private static boolean isCiEnvironment() {
        // 只在 GitHub Actions 环境用 Testcontainers（ubuntu-latest Docker daemon 兼容）。
        // 不用 CI 变量：TRAE IDE 本地也会设置 CI=true，导致误判。
        return "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
    }

    /**
     * 调整本地 MySQL 容器的 session 级别设置，对齐 CI/生产：
     * 1. sql_mode 去掉 NO_ZERO_DATE 和 NO_ZERO_IN_DATE（V1077 '0000-00-00' 字面量兼容）
     * 2. collation_server 设为 utf8mb4_unicode_ci（影响新建数据库）
     * 3. ALTER DATABASE 让已有数据库 xiyu_bid_verify 的临时表列 collation 跟表对齐
     *    （V1092 临时表 tmp_role_mappings JOIN roles.code 不再触发 Error 1267）
     * SET GLOBAL 只对新建连接生效，HikariCP 启动时建立的连接会读到新值。
     * 如有残留 failed migration 记录，调用方需先清理 flyway_schema_history。
     */
    private static void adjustLocalSessionSettings() {
        try (Connection conn = DriverManager.getConnection(LOCAL_JDBC_URL, LOCAL_USERNAME, LOCAL_PASSWORD);
                Statement stmt = conn.createStatement()) {
            stmt.execute("SET GLOBAL sql_mode = '" + TEST_SQL_MODE + "'");
            stmt.execute("SET GLOBAL character_set_server = 'utf8mb4'");
            stmt.execute("SET GLOBAL collation_server = 'utf8mb4_unicode_ci'");
            stmt.execute("ALTER DATABASE xiyu_bid_verify CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "无法连接本地 MySQL 容器调整 session 设置。请确认容器 xiyu-flyway-default 在 13306 端口运行，"
                    + "密码 xiyu_test，库 xiyu_bid_verify 已创建。", e);
        }
    }
}
