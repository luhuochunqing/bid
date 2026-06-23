// Input: e2e profile TENDER_INTAKE DocumentAnalysisInput
// Output: deterministic manual tender extractedData for API-backed E2E coverage
// Pos: biddraftagent/infrastructure/e2e - test-profile adapter, no production activation
package com.xiyu.bid.biddraftagent.infrastructure.e2e;

import com.xiyu.bid.docinsight.application.DocumentAnalysisInput;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import com.xiyu.bid.docinsight.application.DocumentAnalyzer;
import com.xiyu.bid.docinsight.domain.DocInsightProfiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("e2e")
public class E2eTenderIntakeDocumentAnalyzer implements DocumentAnalyzer {

    private static final Map<String, List<String>> FIELD_KEYS = Map.ofEntries(
            Map.entry("title", List.of("标题", "标讯标题", "项目名称")),
            Map.entry("tenderAgency", List.of("招标机构", "代理机构")),
            Map.entry("purchaserName", List.of("业主单位", "招标人", "采购人", "采购单位")),
            Map.entry("region", List.of("总部所在地", "地区")),
            Map.entry("deadline", List.of("报名截止时间", "截止时间", "投标截止时间", "响应截止时间")),
            Map.entry("bidOpeningTime", List.of("开标时间")),
            Map.entry("contactName", List.of("联系人")),
            Map.entry("contactPhone", List.of("手机号", "手机", "移动电话")),
            Map.entry("contactLandline", List.of("座机", "座机号", "固定电话", "联系电话")),
            Map.entry("contactEmail", List.of("邮箱", "电子邮箱")),
            Map.entry("customerType", List.of("客户类型")),
            Map.entry("priority", List.of("优先级"))
    );

    @Override
    public DocumentAnalysisResult analyze(DocumentAnalysisInput input) {
        return new DocumentAnalysisResult(
                input.documentId(),
                extractFields(input.fullText()),
                List.of(),
                input.fullText(),
                List.of()
        );
    }

    @Override
    public boolean supports(String profileCode) {
        return DocInsightProfiles.isTenderIntake(profileCode);
    }

    private Map<String, Object> extractFields(String text) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String line : String.valueOf(text).split("\\R")) {
            String normalized = line.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            int separator = findSeparator(normalized);
            if (separator < 1 || separator >= normalized.length() - 1) {
                continue;
            }
            String key = normalized.substring(0, separator).trim();
            String value = normalized.substring(separator + 1).trim();
            putMappedField(data, key, value);
        }
        return data;
    }

    private int findSeparator(String line) {
        int cn = line.indexOf('：');
        int ascii = line.indexOf(':');
        if (cn < 0) {
            return ascii;
        }
        if (ascii < 0) {
            return cn;
        }
        return Math.min(cn, ascii);
    }

    private void putMappedField(Map<String, Object> data, String sourceKey, String value) {
        if (value.isBlank()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : FIELD_KEYS.entrySet()) {
            if (entry.getValue().contains(sourceKey)) {
                data.putIfAbsent(entry.getKey(), value);
                return;
            }
        }
    }
}
