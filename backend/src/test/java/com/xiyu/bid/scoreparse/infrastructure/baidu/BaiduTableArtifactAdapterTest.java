package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableCell;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableItem;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaiduTableArtifactAdapterTest {

    private static BaiduTableCell cell(int rStart, int rEnd, int cStart, int cEnd, String words) {
        return new BaiduTableCell(rStart, rEnd, cStart, cEnd, words, List.of());
    }

    @Test
    @DisplayName("构建2D网格：含合并单元格时自动填充占位格并能通过CanonicalTableGridFactory校验")
    void shouldBuildComplete2DGridPassingCanonicalTableGridFactory() {
        // 构造一个 3 行 4 列的表格：
        // row 0: 表头 [序号, 评分项目, 评分标准, 分值]
        // row 1: 合并跨2行的数据项 [9, 技术方案(rowspan=2), 9.1 视频播放流畅(5分), 5]
        // row 2: 续行 [占位, 占位, 显示视频。）, 空]
        List<BaiduTableCell> body = List.of(
                cell(0, 0, 0, 0, "序号"),
                cell(0, 0, 1, 1, "评分项目"),
                cell(0, 0, 2, 2, "评分标准"),
                cell(0, 0, 3, 3, "分值"),

                cell(1, 1, 0, 0, "9"),
                cell(1, 2, 1, 1, "技术方案"), // rowspan=2: rStart=1, rEnd=2
                cell(1, 1, 2, 2, "9.1 视频播放流畅要求具备良好画质"),
                cell(1, 1, 3, 3, "5"),

                cell(2, 2, 0, 0, ""),
                // col 1 被技术方案合并覆盖
                cell(2, 2, 2, 2, "显示视频。）"), // 续行
                cell(2, 2, 3, 3, "")
        );

        BaiduTableItem tableItem = new BaiduTableItem(List.of(), List.of(), body, List.of());
        BaiduTableResponse response = new BaiduTableResponse(1, List.of(tableItem), 0, null, 123456L);

        List<CanonicalTable> tables = BaiduTableArtifactAdapter.toCanonicalTables(response, 14, "第四章 评审办法");
        assertThat(tables).hasSize(1);

        CanonicalTable table = tables.get(0);
        assertThat(table.rows()).isEqualTo(3);
        assertThat(table.columns()).isEqualTo(4);
        assertThat(table.cells()).hasSize(12);

        // 验证结构有效性，必须完全通过 CanonicalTableGridFactory
        CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(table);
        assertThat(grid.validation().valid()).isTrue();

        // 验证占位格 (row 2, col 1) 是否正确生成
        CanonicalCellDto coveredCell = table.cells().stream()
                .filter(c -> c.row() == 2 && c.column() == 1)
                .findFirst().orElseThrow();
        assertThat(coveredCell.isMergedContinue()).isTrue();
        assertThat(coveredCell.sourceXmlPath()).isEqualTo("pdf:page=14;row=2;col=1");

        // 验证逻辑行关联：row 2（续行）应该继承 row 1 的 logicalRowId
        CanonicalCellDto row1RuleCell = table.cells().stream()
                .filter(c -> c.row() == 1 && c.column() == 2)
                .findFirst().orElseThrow();
        CanonicalCellDto row2RuleCell = table.cells().stream()
                .filter(c -> c.row() == 2 && c.column() == 2)
                .findFirst().orElseThrow();

        assertThat(row1RuleCell.logicalRowId()).isNotEmpty();
        assertThat(row2RuleCell.logicalRowId()).isEqualTo(row1RuleCell.logicalRowId());
    }
}
