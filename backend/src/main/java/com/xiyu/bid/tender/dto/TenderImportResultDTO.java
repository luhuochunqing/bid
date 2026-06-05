// Input: row-level outcomes from bulk tender import
// Output: aggregate counts and per-row error rows for the controller response
// Pos: dto/响应载体
// 维护声明: 仅承载结果数据，不在此层承担业务规则；新增字段时同步前端 BulkImportDialog 错误表。
package com.xiyu.bid.tender.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 标讯批量导入结果。
 * <p>成功路径返回 {@code totalRows == successCount && errors.isEmpty()}，
 * 失败路径由 {@code TenderImportRollbackException} 携带返回，整批回滚后由 controller 还原为 200 响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenderImportResultDTO {

    /** 模板中除表头外的实际数据行数（不含完全空行）。 */
    private int totalRows;

    /** 成功入库的行数；当任一行失败时为 0（全量回滚）。 */
    private int successCount;

    /** 校验未通过的行数；为 0 即整批成功。 */
    private int failureCount;

    /** 逐行错误明细，按出现顺序排列。 */
    private List<RowError> errors;

    /** 单行单字段错误。{@code row} 对应 Excel 中显示行号（含表头，因此首条数据行是 2）。 */
    public record RowError(int row, String field, String message) {
    }
}
