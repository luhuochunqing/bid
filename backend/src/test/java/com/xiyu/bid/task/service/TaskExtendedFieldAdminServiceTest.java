package com.xiyu.bid.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.task.dto.TaskExtendedFieldReorderRequest;
import com.xiyu.bid.task.dto.TaskExtendedFieldUpsertRequest;
import com.xiyu.bid.task.entity.TaskExtendedField;
import com.xiyu.bid.task.entity.TaskExtendedFieldType;
import com.xiyu.bid.task.repository.TaskExtendedFieldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务扩展字段管理端服务单元测试。
 *
 * <p>使用 {@link DataJpaTest} 加载 JPA + 内存数据库切片，并通过 {@link Import}
 * 注入被测服务与 {@link JacksonAutoConfiguration}（提供 {@link ObjectMapper} bean）。</p>
 *
 * <p>覆盖：create / update / disable / enable / reorder 五条命令路径，以及
 * key 命名规范、key 唯一性、key 不可变、select 必须有 options、reorder 未知 key
 * 等核心不变量。</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({TaskExtendedFieldAdminService.class, JacksonAutoConfiguration.class})
class TaskExtendedFieldAdminServiceTest {

    @Autowired
    private TaskExtendedFieldAdminService service;

    @Autowired
    private TaskExtendedFieldRepository repository;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void seed() {
        seedField("customer_code", "客户编号", TaskExtendedFieldType.text, false, null, null, 10, true);
        seedField("delivery_date", "交付日期", TaskExtendedFieldType.date, true, null, null, 20, true);
        seedField("priority", "优先级", TaskExtendedFieldType.select, false, null,
                "[{\"label\":\"高\",\"value\":\"H\"},{\"label\":\"低\",\"value\":\"L\"}]", 30, true);
        em.flush();
        em.clear();
    }

    private void seedField(String key, String label, TaskExtendedFieldType type,
                           boolean required, String placeholder, String optionsJson,
                           int sortOrder, boolean enabled) {
        TaskExtendedField f = new TaskExtendedField();
        f.setFieldKey(key);
        f.setLabel(label);
        f.setFieldType(type);
        f.setRequired(required);
        f.setPlaceholder(placeholder);
        f.setOptionsJson(optionsJson);
        f.setSortOrder(sortOrder);
        f.setEnabled(enabled);
        em.persist(f);
    }

    private TaskExtendedFieldUpsertRequest req(String key, String label, TaskExtendedFieldType type) {
        TaskExtendedFieldUpsertRequest r = new TaskExtendedFieldUpsertRequest();
        r.setKey(key);
        r.setLabel(label);
        r.setFieldType(type);
        return r;
    }

    // -------------- create --------------

    @Test
    void create_autoSortOrderWhenNotProvided() {
        var r = req("region", "区域", TaskExtendedFieldType.text);
        var dto = service.create(r);
        assertThat(dto.sortOrder()).isEqualTo(40);
        assertThat(dto.enabled()).isTrue();
        assertThat(dto.required()).isFalse();
    }

    @Test
    void create_rejectsInvalidKeyRegex() {
        // 大写开头
        assertThatThrownBy(() -> service.create(req("BadKey", "x", TaskExtendedFieldType.text)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法 key");

        // 数字开头
        assertThatThrownBy(() -> service.create(req("1abc", "x", TaskExtendedFieldType.text)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法 key");

        // 含连字符
        assertThatThrownBy(() -> service.create(req("bad-key", "x", TaskExtendedFieldType.text)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法 key");
    }

    @Test
    void create_rejectsDuplicateKey() {
        var r = req("customer_code", "重复 key", TaskExtendedFieldType.text);
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void create_rejectsSelectWithoutOptions() {
        var r = req("severity", "严重程度", TaskExtendedFieldType.select);
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select");
    }

    @Test
    void create_storesOptionsAsJson() {
        var r = req("severity", "严重程度", TaskExtendedFieldType.select);
        r.setOptions(List.of(
                new TaskExtendedFieldUpsertRequest.OptionItem("高", "H"),
                new TaskExtendedFieldUpsertRequest.OptionItem("低", "L")
        ));
        var dto = service.create(r);
        em.flush();
        em.clear();

        assertThat(dto.options()).hasSize(2);
        assertThat(dto.options().get(0).label()).isEqualTo("高");
        assertThat(dto.options().get(0).value()).isEqualTo("H");

        var stored = repository.findById("severity").orElseThrow();
        assertThat(stored.getOptionsJson()).contains("\"label\":\"高\"");
        assertThat(stored.getOptionsJson()).contains("\"value\":\"H\"");
    }

    // -------------- update --------------

    @Test
    void update_labelAndRequired() {
        var r = new TaskExtendedFieldUpsertRequest();
        r.setLabel("客户编号（更新）");
        r.setRequired(true);
        var dto = service.update("customer_code", r);

        assertThat(dto.label()).isEqualTo("客户编号（更新）");
        assertThat(dto.required()).isTrue();
        // 未传字段保持不变
        assertThat(dto.fieldType()).isEqualTo(TaskExtendedFieldType.text.name());
        assertThat(dto.sortOrder()).isEqualTo(10);
    }

    @Test
    void update_keyIsImmutable() {
        var r = new TaskExtendedFieldUpsertRequest();
        r.setKey("brand_new_key"); // 应被忽略
        r.setLabel("依然是客户编号");
        var dto = service.update("customer_code", r);
        em.flush();
        em.clear();

        // 主键仍是路径变量传入的 key
        assertThat(dto.key()).isEqualTo("customer_code");
        assertThat(repository.findById("customer_code")).isPresent();
        assertThat(repository.findById("brand_new_key")).isEmpty();
    }

    @Test
    void update_typeFromTextToSelectWithoutOptions_rejected() {
        // customer_code 当前是 text，无 optionsJson；切到 select 但不带 options 应被拒
        var r = new TaskExtendedFieldUpsertRequest();
        r.setFieldType(TaskExtendedFieldType.select);
        assertThatThrownBy(() -> service.update("customer_code", r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select");
    }

    @Test
    void update_typeChangePreservesOtherFields() {
        // priority 已经是 select 且有 options；切到 textarea 时只改 fieldType，其余保留
        var r = new TaskExtendedFieldUpsertRequest();
        r.setFieldType(TaskExtendedFieldType.textarea);
        var dto = service.update("priority", r);

        assertThat(dto.fieldType()).isEqualTo(TaskExtendedFieldType.textarea.name());
        assertThat(dto.label()).isEqualTo("优先级");
        assertThat(dto.sortOrder()).isEqualTo(30);
        // optionsJson 没传 -> 保留原值（仍可被 toDto 解析）
        assertThat(dto.options()).hasSize(2);
    }

    // -------------- disable / enable --------------

    @Test
    void disable_flipsEnabledFalse() {
        var dto = service.disable("customer_code");
        assertThat(dto.enabled()).isFalse();
        em.flush();
        em.clear();
        assertThat(repository.findById("customer_code").orElseThrow().getEnabled()).isFalse();
    }

    @Test
    void enable_flipsEnabledTrue() {
        seedField("legacy", "历史字段", TaskExtendedFieldType.text, false, null, null, 5, false);
        em.flush();
        em.clear();

        var dto = service.enable("legacy");
        assertThat(dto.enabled()).isTrue();
    }

    // -------------- reorder --------------

    @Test
    void reorder_batchUpdatesSortOrder() {
        var req = new TaskExtendedFieldReorderRequest();
        req.setItems(List.of(
                new TaskExtendedFieldReorderRequest.Item("customer_code", 100),
                new TaskExtendedFieldReorderRequest.Item("priority", 200)
        ));
        service.reorder(req);
        em.flush();
        em.clear();

        assertThat(repository.findById("customer_code").orElseThrow().getSortOrder()).isEqualTo(100);
        assertThat(repository.findById("priority").orElseThrow().getSortOrder()).isEqualTo(200);
        // 未提及的字段保持原值
        assertThat(repository.findById("delivery_date").orElseThrow().getSortOrder()).isEqualTo(20);
    }

    @Test
    void reorder_rejectsUnknownKey() {
        var req = new TaskExtendedFieldReorderRequest();
        req.setItems(List.of(
                new TaskExtendedFieldReorderRequest.Item("customer_code", 1),
                new TaskExtendedFieldReorderRequest.Item("does_not_exist", 2)
        ));
        assertThatThrownBy(() -> service.reorder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does_not_exist");
    }
}
