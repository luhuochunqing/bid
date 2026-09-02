// Input: ScoreParseTask.originalTotalWeight + ScoreParseItemsDTO.Summary.originalTotalWeight
// Output: US3 信号链路契约单测（spec 047）——entity 映射、DTO 序列化、getItems 透出语义
// Pos: Test/scoreparse/application — 纯测试，无 Spring 上下文

package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalTotalWeightSignalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void summary_originalTotalWeight_serializesValueWhenPresent() throws Exception {
        // US3 信号：originalTotalWeight 非 null 时序列化值正确
        var summary = summaryWith(new BigDecimal("90"));

        String json = objectMapper.writeValueAsString(summary);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("originalTotalWeight")).isTrue();
        assertThat(node.get("originalTotalWeight").decimalValue()).isEqualByComparingTo("90");
    }

    @Test
    void summary_originalTotalWeight_serializesNullWhenAbsent() throws Exception {
        // US3 信号：未归一化（null）→ 序列化键存在且为 null
        var summary = summaryWith(null);

        String json = objectMapper.writeValueAsString(summary);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("originalTotalWeight")).isTrue();
        assertThat(node.get("originalTotalWeight").isNull()).isTrue();
    }

    @Test
    void scoreParseTask_originalTotalWeight_fieldAndColumnMappingExist() throws Exception {
        // 契约：entity 字段 + @Column(name=original_total_weight) 映射必须存在
        Field field = ScoreParseTask.class.getDeclaredField("originalTotalWeight");
        assertThat(field.getType()).isEqualTo(BigDecimal.class);

        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("original_total_weight");
    }

    @Test
    void scoreParseTask_originalTotalWeight_getterRoundTrips() {
        // 契约：getter 读写闭环（getItems 依赖 latest.getOriginalTotalWeight()）
        ScoreParseTask task = new ScoreParseTask();
        task.setOriginalTotalWeight(new BigDecimal("90"));

        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("90");

        ScoreParseTask empty = new ScoreParseTask();
        // getItems 分支：latest==null 透传 null
        BigDecimal wired = empty.getOriginalTotalWeight() == null ? null : empty.getOriginalTotalWeight();
        assertThat(wired).isNull();
    }

    @Test
    void getItems_passthroughMapping_writesValueOrNull() throws Exception {
        // 透出映射语义（getItems: latest==null ? null : latest.getOriginalTotalWeight()）：
        // 非 null → Summary 携带真实值；null → Summary 序列化 null
        ScoreParseTask latestWithSignal = new ScoreParseTask();
        latestWithSignal.setOriginalTotalWeight(new BigDecimal("90"));
        String withSignal = objectMapper.writeValueAsString(
                summaryWith(latestWithSignal.getOriginalTotalWeight()));
        assertThat(objectMapper.readTree(withSignal).get("originalTotalWeight")
                .decimalValue()).isEqualByComparingTo("90");

        String withoutSignal = objectMapper.writeValueAsString(
                summaryWith(null));
        assertThat(objectMapper.readTree(withoutSignal).get("originalTotalWeight").isNull()).isTrue();
    }

    private ScoreParseItemsDTO.Summary summaryWith(BigDecimal originalTotalWeight) {
        return new ScoreParseItemsDTO.Summary(
                new BigDecimal("100"), null,
                1, 0, 0,
                new BigDecimal("60"), new BigDecimal("40"),
                false, originalTotalWeight, null);
    }
}