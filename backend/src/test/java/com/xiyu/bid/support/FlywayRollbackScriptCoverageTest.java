package com.xiyu.bid.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayRollbackScriptCoverageTest {

    @Test
    void everyMysqlMigrationHasRollbackScript() throws IOException {
        assertRollbackCoverage("db/migration-mysql", "db/rollback/migration-mysql");
    }

    private void assertRollbackCoverage(String migrationDir, String rollbackDir) throws IOException {
        Path resourceRoot = Path.of("src/main/resources");
        Path migrationRoot = resourceRoot.resolve(migrationDir);
        Path rollbackRoot = resourceRoot.resolve(rollbackDir);

        List<String> rollbackProblems;
        try (Stream<Path> migrations = Files.list(migrationRoot)) {
            rollbackProblems = migrations
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> rollbackProblem(rollbackRoot, path.getFileName().toString()))
                    .filter(problem -> !problem.isBlank())
                    .sorted()
                    .toList();
        }

        assertThat(rollbackProblems)
                .as("%s must contain a reviewed rollback script for every SQL file under %s", rollbackDir, migrationDir)
                .isEmpty();
    }

    private String rollbackProblem(Path rollbackRoot, String migrationFileName) {
        String rollbackFileName = rollbackFileName(migrationFileName);
        Path rollbackPath = rollbackRoot.resolve(rollbackFileName);
        if (!Files.isRegularFile(rollbackPath)) {
            return rollbackFileName + " missing";
        }

        try {
            String rollbackSql = Files.readString(rollbackPath);
            if (!rollbackSql.contains("-- Input:") || !rollbackSql.contains(migrationFileName)) {
                return rollbackFileName + " missing source header";
            }
            if (!rollbackSql.contains("DROP ")
                    && !rollbackSql.contains("ALTER TABLE ")
                    && !rollbackSql.contains("UPDATE ")
                    && !rollbackSql.contains("Data rollback required")
                    && !rollbackSql.contains("Manual rollback required")
                    && !rollbackSql.contains("No-op rollback")) {
                return rollbackFileName + " has no rollback action or review note";
            }
        } catch (IOException ex) {
            return rollbackFileName + " unreadable: " + ex.getMessage();
        }
        return "";
    }

    private String rollbackFileName(String migrationFileName) {
        // Versioned migrations (V*) become U*; baseline migrations (B*) become
        // UB* so a Vn / Bn pair never collides on the same Un prefix in the
        // rollback dir. Mirrors scripts/generate-flyway-rollback-scripts.mjs.
        if (migrationFileName.startsWith("B")) {
            return "U" + migrationFileName;
        }
        return migrationFileName.replaceFirst("^V", "U");
    }
}
