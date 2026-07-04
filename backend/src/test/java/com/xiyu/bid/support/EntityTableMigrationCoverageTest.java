package com.xiyu.bid.support;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 防御性守卫：每个 {@link jakarta.persistence.Table @Table} 实体必须有对应的建表迁移。
 *
 * <p>工程背景：2026-07-04 第 40 次部署 P0 事故（CO-483/484 PR !1637）——
 * {@code BidReviewAssignmentEntity} 标了 {@code @Table(name = "bid_review_assignment")}，
 * 但建表迁移 {@code V123__add_bid_review_assignment.sql} 被误放在 {@code db/migration/}
 * （历史目录，Flyway 不读），且版本号撞了历史基线，导致表从未被创建，
 * 运行时 {@code /api/projects/{id}/stage} 接口 500。
 *
 * <p>本测试扫描所有 {@code @Table(name = "xxx")} 实体，验证 {@code migration-mysql/}
 * 或 {@code migration/}（历史 fallback）目录中存在 {@code CREATE TABLE xxx} 迁移。
 *
 * <p>防御纵深（与 pre-push-gate.sh §3.7 互补）：
 * <ul>
 *   <li>pre-push-gate.sh §3.7 — 在 push 时拦截 <em>commit 范围内新增</em>的 V/B 文件误放 db/migration/</li>
 *   <li>本测试 — 在 CI/本地 mvn test 时扫描 <em>所有 @Table 实体</em>是否有 CREATE TABLE 迁移</li>
 * </ul>
 *
 * <p>不重复检查 db/migration/ 中历史遗留文件（V111~V122 已应用但未清理），
 * 那部分由 pre-push-gate.sh §3.7 拦截新增，历史清理作为独立 tech-debt 处理。
 *
 * <p>exemptions 列出特殊场景：表由其他框架管理（如 Flyway baseline 之前的表）、
 * 视图、外部表等。新增 exemption 必须在 PR 描述中说明理由。
 */
class EntityTableMigrationCoverageTest {

    private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
    private static final Path ACTIVE_MIGRATION_DIR = RESOURCE_ROOT.resolve("db/migration-mysql");
    private static final Path LEGACY_MIGRATION_DIR = RESOURCE_ROOT.resolve("db/migration");

    /**
     * 豁免表清单：这些表名对应的实体不要求在 migration-mysql/ 中有建表迁移。
     * 新增豁免必须在 PR 描述中说明理由（如：表由 Flyway baseline 之前的手动脚本创建）。
     */
    private static final Set<String> TABLE_MIGRATION_EXEMPTIONS = Set.of(
        // 基线 B73 之前已存在的表（历史遗留，无对应 V*.sql 建表迁移）
        "users",
        "project_score_previews",
        // 历史废弃实体：表名带 _deprecated 后缀，代码仍被 Repository 引用但已无业务流程使用
        // tech-debt: 应在独立任务中删除整个 brandauth 模块
        "brand_authorization_deprecated",
        // 添加新豁免前请先尝试补建表迁移，豁免是最后手段
        // 历史豁免理由必须记录在 PR 描述和本注释中
        "flyway_schema_history"
    );

    // 支持以下格式（含可选反引号、可选 schema 前缀、可选 IF NOT EXISTS）：
    //   create table templates (
    //   CREATE TABLE IF NOT EXISTS `bid_review_assignment` (
    //   CREATE TABLE `xiyu_bid_main`.users (  ← schema 前缀无反引号也支持
    // 注意：[\`\\w]* 之类的贪婪前缀会消费整个表名导致 group(1) 只剩末尾字符（CO-483 事故 P2 修复）。
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
        "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(?:\\w+\\.)?(\\w+)`?",
        Pattern.CASE_INSENSITIVE
    );

    private final JavaClasses productionClasses = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.xiyu.bid");

    @Test
    void every_table_annotated_entity_has_create_table_migration() throws IOException {
        List<String> violations = productionClasses.stream()
            .filter(this::hasTableAnnotation)
            .map(this::extractTableName)
            .filter(name -> !name.isBlank())
            .filter(name -> !TABLE_MIGRATION_EXEMPTIONS.contains(name))
            .distinct()
            .sorted()
            .map(this::findMigrationProblem)
            .filter(problem -> !problem.isBlank())
            .toList();

        assertThat(violations)
            .as("@Table 实体必须有对应的 CREATE TABLE 迁移。"
                + "缺失迁移会导致运行时 SQLSyntaxErrorException: Table doesn't exist。"
                + "如新增实体，请在 db/migration-mysql/ 添加 V<version>__create_<table>.sql。"
                + "如是历史遗留表，请加豁免到 TABLE_MIGRATION_EXEMPTIONS 并在 PR 描述说明理由。")
            .isEmpty();
    }

    // 附加检查「db/migration/ 中无 V/B 文件」已迁移到 pre-push-gate.sh §3.7：
    //   - pre-push 拦截 commit 范围内新增（覆盖所有 worktree，不依赖 install-githooks.sh）
    //   - 历史遗留 V111~V122 作为独立 tech-debt 清理，不在本测试范围
    // 如需全量审计 legacy 目录，请运行：bash scripts/check-flyway-migration-dir.sh

    private boolean hasTableAnnotation(JavaClass javaClass) {
        // 直接检查类的字节码注解（ArchUnit 会反射加载）
        try {
            Class<?> loaded = Class.forName(javaClass.getName());
            return loaded.isAnnotationPresent(jakarta.persistence.Table.class)
                || loaded.isAnnotationPresent(jakarta.persistence.Entity.class);
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private String extractTableName(JavaClass javaClass) {
        try {
            Class<?> loaded = Class.forName(javaClass.getName());
            jakarta.persistence.Table tableAnnotation = loaded.getAnnotation(jakarta.persistence.Table.class);
            if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
                return tableAnnotation.name();
            }
            // @Entity 无 @Table 时，表名 = 类名首字母小写（Hibernate 默认策略）
            if (loaded.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                String simpleName = loaded.getSimpleName();
                return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
            }
        } catch (ClassNotFoundException ex) {
            return "";
        }
        return "";
    }

    private String findMigrationProblem(String tableName) {
        boolean foundInActive = hasCreateTableInDir(ACTIVE_MIGRATION_DIR, tableName);
        if (foundInActive) {
            return ""; // 在活跃目录找到，无问题
        }
        boolean foundInLegacy = hasCreateTableInDir(LEGACY_MIGRATION_DIR, tableName);
        if (foundInLegacy) {
            return "实体表 " + tableName + " 的 CREATE TABLE 迁移误放在 db/migration/（历史目录，Flyway 不读）。"
                + "修复：git mv 到 db/migration-mysql/ 并用 next-migration-version.sh 重新分配版本号。"
                + "历史事故：2026-07-04 V123 add_bid_review_assignment 事故。";
        }
        return "实体表 " + tableName + " 在 db/migration-mysql/ 和 db/migration/ 都找不到 CREATE TABLE 迁移。"
            + "修复：在 db/migration-mysql/ 创建 V<version>__create_<table>.sql";
    }

    private boolean hasCreateTableInDir(Path migrationDir, String tableName) {
        if (!Files.isDirectory(migrationDir)) {
            return false;
        }
        try (Stream<Path> sqlFiles = Files.list(migrationDir)) {
            return sqlFiles
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .anyMatch(path -> containsCreateTableFor(path, tableName));
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean containsCreateTableFor(Path sqlFile, String tableName) {
        try {
            String content = Files.readString(sqlFile);
            Matcher matcher = CREATE_TABLE_PATTERN.matcher(content);
            while (matcher.find()) {
                String createdTable = matcher.group(1);
                if (createdTable.equalsIgnoreCase(tableName)) {
                    return true;
                }
            }
        } catch (IOException ex) {
            // 读取失败视为未找到
        }
        return false;
    }
}
