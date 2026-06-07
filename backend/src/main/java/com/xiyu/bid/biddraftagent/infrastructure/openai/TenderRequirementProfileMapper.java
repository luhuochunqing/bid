// Input: TenderRequirementOutput (LLM POJO) and domain item snapshots
// Output: TenderRequirementProfile domain objects and DocumentAnalysisResult items
// Pos: biddraftagent/infrastructure/openai
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;

import java.util.List;

final class TenderRequirementProfileMapper {

    private TenderRequirementProfileMapper() {
    }

    static TenderRequirementProfile toTenderProfile(TenderRequirementOutput output, List<String> defaultPath) {
        String defaultPathStr = String.join(" > ", defaultPath);
        return new TenderRequirementProfile(
                output.projectName, output.tenderTitle, output.tenderScope, output.purchaserName,
                TenderFieldParser.parseBudget(output.budget),
                output.region, output.industry,
                TenderFieldParser.parsePublishDate(output.publishDate),
                TenderFieldParser.parseDeadline(output.deadline),
                nullToList(output.qualificationRequirements),
                nullToList(output.technicalRequirements),
                nullToList(output.commercialRequirements),
                nullToList(output.scoringCriteria),
                nullToList(output.scoringCriteriaItems).stream()
                        .map(ScoringCriterionMapper::toDomain)
                        .toList(),
                output.deadlineText,
                nullToList(output.requiredMaterials),
                nullToList(output.riskPoints),
                nullToList(output.tags),
                nullToList(output.requirementItems).stream()
                        .map(item -> toTenderItem(item, defaultPathStr)).toList()
        );
    }

    static TenderRequirementItemSnapshot toTenderItem(TenderRequirementItemOutput item, String defaultPath) {
        String finalPath = item.sectionPath;
        if ((finalPath == null || finalPath.isBlank()) && !defaultPath.isEmpty()) {
            finalPath = defaultPath;
        }
        return new TenderRequirementItemSnapshot(
                item.category, item.title, item.content, item.mandatory,
                item.sourceExcerpt, item.confidence, finalPath
        );
    }

    static DocumentAnalysisResult.AnalysisRequirementItem toAnalysisItem(TenderRequirementItemSnapshot item) {
        return new DocumentAnalysisResult.AnalysisRequirementItem(
                item.category(), item.title(), item.content(), item.mandatory(),
                item.sourceExcerpt(), item.confidence(), item.sectionPath()
        );
    }

    static TenderRequirementItemSnapshot toSnapshot(DocumentAnalysisResult.AnalysisRequirementItem item) {
        return new TenderRequirementItemSnapshot(
                item.category(), item.title(), item.content(), item.mandatory(),
                item.sourceExcerpt(), item.confidence(), item.sectionPath()
        );
    }

    private static <T> List<T> nullToList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
