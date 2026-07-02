package com.xiyu.bid.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskEntityMappingTest {

    @Test
    void status_shouldMapAsVarcharToMatchMysqlMigration() throws Exception {
        Field status = Task.class.getDeclaredField("status");
        // 00f033b0e 后统一用 columnDefinition 模式，不再用 @JdbcTypeCode
        Enumerated enumerated = status.getAnnotation(Enumerated.class);
        assertNotNull(enumerated);
        assertEquals(EnumType.STRING, enumerated.value());
        Column column = status.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals(32, column.length());
        assertEquals("varchar(32)", column.columnDefinition());
    }
}
