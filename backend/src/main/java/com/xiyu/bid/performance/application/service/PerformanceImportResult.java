package com.xiyu.bid.performance.application.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 业绩批量导入结果
 */
public class PerformanceImportResult {

    public int successCount;
    public int failureCount;
    public List<ImportFailure> failures = new ArrayList<>();

    public record ImportFailure(int rowNum, String contractName, String reason) {}
}
