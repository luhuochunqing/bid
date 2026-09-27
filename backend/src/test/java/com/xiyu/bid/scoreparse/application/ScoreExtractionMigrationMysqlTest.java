package com.xiyu.bid.scoreparse.application;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ScoreExtractionMigrationMysqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("extraction");
    @Test void forwardPreservesHistoricalRowsAndRollbackRefusesUnknownWeights() throws Exception {
        try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var s=c.createStatement()) {
            s.execute("CREATE TABLE score_parse_fact(id BIGINT PRIMARY KEY, weight DECIMAL(6,2) NOT NULL)");
            s.execute("CREATE TABLE score_item(id BIGINT PRIMARY KEY)");
            s.execute("INSERT INTO score_parse_fact VALUES(1,6)");
            sql(s,"db/migration-mysql/V1219__score_extraction_versions.sql");
            try(var r=s.executeQuery("SELECT weight,item_payload FROM score_parse_fact WHERE id=1")) {assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("6");assertThat(r.getString(2)).isNull();}
            s.execute("INSERT INTO score_parse_fact(id,weight,item_payload,payload_version) VALUES(2,NULL,'{\"rawMaximum\":null}',1)");
            assertThatThrownBy(() -> sql(s,"db/rollback/migration-mysql/U1219__score_extraction_versions.sql")).isInstanceOf(SQLException.class);
            try(var r=s.executeQuery("SELECT weight FROM score_parse_fact WHERE id=2")){r.next();assertThat(r.getBigDecimal(1)).isNull();}
            s.execute("DELETE FROM score_parse_fact WHERE id=2");
            sql(s,"db/rollback/migration-mysql/U1219__score_extraction_versions.sql");
            assertThatThrownBy(() -> s.execute("INSERT INTO score_parse_fact VALUES(2,NULL)")).isInstanceOf(SQLException.class);
        }
    }
    static void sql(Statement s,String name)throws Exception {
        try(var in=ScoreExtractionMigrationMysqlTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            for(String q:new String(in.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\n]*$","").split(";")) if(!q.isBlank())s.execute(q);
        }
    }
}
