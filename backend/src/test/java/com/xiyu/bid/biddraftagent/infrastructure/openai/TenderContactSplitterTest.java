package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenderContactSplitterTest {

    @Test
    void shouldSplitTwoContactNamesWithDunhao() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "姜经理、段经理");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("姜经理");
        assertThat(data.get("contactName2")).isEqualTo("段经理");
    }

    @Test
    void shouldSplitTwoContactNamesWithComma() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三,李四");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张三");
        assertThat(data.get("contactName2")).isEqualTo("李四");
    }

    @Test
    void shouldSplitTwoContactNamesWithChineseComma() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三，李四");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张三");
        assertThat(data.get("contactName2")).isEqualTo("李四");
    }

    @Test
    void shouldNotSplitWhenContactName2AlreadyExists() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "姜经理、段经理");
        data.put("contactName2", "王主任");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("姜经理、段经理");
        assertThat(data.get("contactName2")).isEqualTo("王主任");
    }

    @Test
    void shouldNotSplitSingleContactName() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张经理");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张经理");
        assertThat(data.get("contactName2")).isNull();
    }

    @Test
    void shouldNotSplitWhenNoSeparator() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三丰");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张三丰");
        assertThat(data.get("contactName2")).isNull();
    }

    @Test
    void shouldNotSplitWhenContainsNumbers() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三123、李四");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张三123、李四");
        assertThat(data.get("contactName2")).isNull();
    }

    @Test
    void shouldSplitPhoneWhenNamesSplit() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三、李四");
        data.put("contactPhone", "13800138000、13900139000");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张三");
        assertThat(data.get("contactName2")).isEqualTo("李四");
        assertThat(data.get("contactPhone")).isEqualTo("13800138000");
        assertThat(data.get("contactPhone2")).isEqualTo("13900139000");
    }

    @Test
    void shouldNotSplitPhoneWhenCountMismatch() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三、李四");
        data.put("contactPhone", "13800138000");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张三");
        assertThat(data.get("contactName2")).isEqualTo("李四");
        assertThat(data.get("contactPhone")).isEqualTo("13800138000");
        assertThat(data.get("contactPhone2")).isNull();
    }

    @Test
    void shouldNotOverrideExistingPhone2() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三、李四");
        data.put("contactPhone", "13800138000、13900139000");
        data.put("contactPhone2", "13700137000");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactPhone2")).isEqualTo("13700137000");
    }

    @Test
    void shouldSplitEmailWhenNamesSplit() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三、李四");
        data.put("contactEmail", "zhangsan@example.com、lisi@example.com");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactEmail")).isEqualTo("zhangsan@example.com");
        assertThat(data.get("contactEmail2")).isEqualTo("lisi@example.com");
    }

    @Test
    void shouldSplitLandlineWhenNamesSplit() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三、李四");
        data.put("contactLandline", "010-12345678、021-87654321");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactLandline")).isEqualTo("010-12345678");
        assertThat(data.get("contactLandline2")).isEqualTo("021-87654321");
    }

    @Test
    void shouldHandleNullContactName() {
        Map<String, Object> data = new HashMap<>();
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data).isEmpty();
    }

    @Test
    void shouldHandleEmptyContactName() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("");
        assertThat(data.get("contactName2")).isNull();
    }

    @Test
    void shouldNotSplitWhenSecondPartIsEmpty() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三、");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张三、");
        assertThat(data.get("contactName2")).isNull();
    }

    @Test
    void shouldSplitWithSlashSeparator() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "张三/李四");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName")).isEqualTo("张三");
        assertThat(data.get("contactName2")).isEqualTo("李四");
    }

    @Test
    void shouldNotSplitTooLongName() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "一二三四五六七八九十壹贰叁肆伍陆柒捌玖拾贰拾壹、李四");
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
        assertThat(data.get("contactName2")).isNull();
    }
}
