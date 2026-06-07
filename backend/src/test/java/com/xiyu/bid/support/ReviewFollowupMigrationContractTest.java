package com.xiyu.bid.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewFollowupMigrationContractTest {

    @Test
    void v77_ShouldAddContractBorrowVersionAndCustomerTypeStatusIndex() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration-mysql/V77__contract_borrow_and_customer_type_indexes.sql"
        ));

        assertThat(sql).contains("add column version");
        assertThat(sql).contains("idx_project_customer_type_status");
        assertThat(sql).contains("customer_type, status");
    }

    @Test
    void v78_ShouldAddNormalizedColumnsAndIndexesForTenderSearch() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration-mysql/V78__tender_normalized_search_indexes.sql"
        ));

        assertThat(sql).contains("source_normalized");
        assertThat(sql).contains("region_normalized");
        assertThat(sql).contains("industry_normalized");
        assertThat(sql).contains("purchaser_name_normalized");
        assertThat(sql).contains("search_text_normalized");
        assertThat(sql).contains("idx_tender_status_region_industry_normalized");
    }

}
