package com.xiyu.bid.personnel.application.importexcel;

import com.xiyu.bid.personnel.domain.importvalidation.ParsedCertificateRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedEducationRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedPersonnelRow;
import com.xiyu.bid.personnel.domain.importvalidation.ValidationResult;

import java.util.List;

/**
 * Excel 解析 + 校验结果（应用层结果对象）
 * 包含原始解析后的三张表数据 + 校验结果
 */
public record ImportParseResult(
        List<ParsedPersonnelRow> personnelRows,
        List<ParsedEducationRow> educationRows,
        List<ParsedCertificateRow> certificateRows,
        ValidationResult validationResult
) {

    public boolean hasBlockingErrors() {
        return validationResult != null && validationResult.hasBlockingErrors();
    }

    public boolean isValidForImport() {
        return !hasBlockingErrors();
    }
}
