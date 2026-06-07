package com.xiyu.bid.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway migration strategy for dev profile.
 *
 * flyway.repair() IS DELIBERATELY DISABLED — do NOT re-enable it.
 *
 * Reason: repair() marks successfully-applied migrations as DELETE type,
 * causing Flyway to re-attempt them on every restart. This produces
 * "Duplicate column" failures and corrupts the schema history.
 *
 * If a migration truly fails in dev:
 *   1. Manually clean schema history: DELETE FROM flyway_schema_history WHERE success=0
 *   2. Fix the migration SQL
 *   3. Mark it applied: INSERT INTO flyway_schema_history (..., success=1)
 *
 * CI gate: flyway-migrate-dryrun workflow runs all migrations from baseline
 * on a fresh MySQL 8.0, catching broken migrations before merge.
 */
@Configuration
@Profile("dev")
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // DO NOT add flyway.repair() here. See class-level javadoc.
            flyway.migrate();
        };
    }
}
