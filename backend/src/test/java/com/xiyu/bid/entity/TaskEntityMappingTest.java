package com.xiyu.bid.entity;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskEntityMappingTest {

    @Test
    void status_shouldMapAsVarcharToMatchMysqlMigration() throws Exception {
        Field status = Task.class.getDeclaredField("status");

        JdbcTypeCode jdbcTypeCode = status.getAnnotation(JdbcTypeCode.class);
        Column column = status.getAnnotation(Column.class);

        assertNotNull(jdbcTypeCode);
        assertEquals(SqlTypes.VARCHAR, jdbcTypeCode.value());
        assertNotNull(column);
        assertEquals(32, column.length());
    }
}
