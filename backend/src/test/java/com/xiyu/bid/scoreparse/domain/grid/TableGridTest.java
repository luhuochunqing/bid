package com.xiyu.bid.scoreparse.domain.grid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TableGrid - 表格网格结构与左右补空对齐策略")
class TableGridTest {

    @Test
    @DisplayName("首列为纯数字或带点标号时禁止左补空")
    void firstCol_numericCode_noLeftPadding() {
        // maxCols = 4
        List<List<String>> rows = List.of(
                List.of("序号", "评分项", "评分标准", "分值"),
                List.of("1", "财务状况", "10") // 只有 3 列，首列为 1，尾列为 10
        );
        TableGrid grid = new TableGrid(rows);
        // 第一行
        assertThat(grid.row(1).get(0)).isEqualTo("1");
        assertThat(grid.row(1).get(1)).isEqualTo("财务状况");
        assertThat(grid.row(1).get(2)).isEqualTo("10");
        assertThat(grid.row(1).get(3)).isEmpty(); // 尾部右补空，而非首部左补空
    }

    @Test
    @DisplayName("首列为中文数字章节或顿号标号时禁止左补空")
    void firstCol_chineseOr顿号Code_noLeftPadding() {
        List<List<String>> rows = List.of(
                List.of("大项", "小项", "标准", "最高分"),
                List.of("一、", "诚信评价", "0"),
                List.of("1、", "履约情况", "5"),
                List.of("（一）", "特定资质", "10")
        );
        TableGrid grid = new TableGrid(rows);
        assertThat(grid.row(1).get(0)).isEqualTo("一、");
        assertThat(grid.row(2).get(0)).isEqualTo("1、");
        assertThat(grid.row(3).get(0)).isEqualTo("（一）");
    }

    @Test
    @DisplayName("尾列为负数扣分项（-10）时禁止被误判为分值列而触发左补空")
    void lastCol_negativeDeduction_noLeftPadding() {
        List<List<String>> rows = List.of(
                List.of("项目", "评审要素", "评审内容", "最低分", "最高分"),
                List.of("失信惩戒", "重大负面记录扣5-10分", "-10") // 3 列，尾列为 -10
        );
        TableGrid grid = new TableGrid(rows);
        // 不应左补空，第 0 列保留为失信惩戒
        assertThat(grid.row(1).get(0)).isEqualTo("失信惩戒");
        assertThat(grid.row(1).get(1)).isEqualTo("重大负面记录扣5-10分");
        assertThat(grid.row(1).get(2)).isEqualTo("-10");
        assertThat(grid.row(1).get(3)).isEmpty();
        assertThat(grid.row(1).get(4)).isEmpty();
    }

    @Test
    @DisplayName("首列无编号特征且尾列为正分值时，正常触发左补空")
    void noCode_positiveWeight_padsLeft() {
        List<List<String>> rows = List.of(
                List.of("大项", "小项", "标准", "分值"),
                List.of("供货方案保障评价", "5") // 2 列，首列无编号，尾列为 5
        );
        TableGrid grid = new TableGrid(rows);
        // 左补空 2 列
        assertThat(grid.row(1).get(0)).isEmpty();
        assertThat(grid.row(1).get(1)).isEmpty();
        assertThat(grid.row(1).get(2)).isEqualTo("供货方案保障评价");
        assertThat(grid.row(1).get(3)).isEqualTo("5");
    }
}
