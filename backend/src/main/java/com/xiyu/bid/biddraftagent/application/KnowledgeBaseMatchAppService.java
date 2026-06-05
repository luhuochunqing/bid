// Input: projectId → 四库联动匹配结果
// Output: KnowledgeBaseMatchResult（四库匹配聚合结果）
// Pos: biddraftagent/application — 四库联动匹配编排服务

package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.validation.BrandAuthMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult.KnowledgeBaseSummary;
import com.xiyu.bid.biddraftagent.domain.validation.PerformanceMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.PersonnelCertMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationMatchResult;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationMatcher;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 四库联动匹配编排服务。
 * 加载数据快照 → 反序列化 profile → 调用 4 个 Matcher → 聚合结果。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseMatchAppService {

    private final KnowledgeBaseDataLoader dataLoader;
    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final BidDraftAgentJsonCodec jsonCodec;

    public KnowledgeBaseMatchResult matchForProject(Long projectId) {
        var snapshot = snapshotRepository
                .findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .orElse(null);
        if (snapshot == null || snapshot.getProfileJson() == null) {
            return emptyResult();
        }
        TenderRequirementProfile profile = jsonCodec.fromJson(
                snapshot.getProfileJson(), TenderRequirementProfile.class);
        if (profile == null) {
            return emptyResult();
        }

        KnowledgeBaseDataLoader.KnowledgeBaseDataSnapshot data = dataLoader.loadAll();
        LocalDate today = LocalDate.now();

        // 资质库：纯核心匹配，传入资质列表 + 参考日期
        QualificationMatchResult qualResult = new QualificationMatcher().match(
                profile.qualificationRequirements(), data.qualifications(), today);

        // 人员/品牌/业绩：跨维度匹配所有要求（资质 + 技术 + 商务）
        List<String> allRequirements = combineRequirements(profile);

        PersonnelCertMatcher.PersonnelMatchResult personnelResult =
                new PersonnelCertMatcher().match(
                        allRequirements, data.personnelCerts(), today);

        BrandAuthMatcher.BrandAuthMatchResult brandResult =
                new BrandAuthMatcher().match(
                        allRequirements, data.brandAuthorizations(), today);

        PerformanceMatcher.PerformanceMatchResult perfResult =
                new PerformanceMatcher().match(
                        allRequirements, data.performanceRecords(), today);

        KnowledgeBaseSummary summary = KnowledgeBaseSummary.compute(
                qualResult, personnelResult, brandResult, perfResult);

        return new KnowledgeBaseMatchResult(
                qualResult, personnelResult, brandResult, perfResult, summary);
    }

    private KnowledgeBaseMatchResult emptyResult() {
        QualificationMatchResult emptyQual = new QualificationMatchResult(List.of());
        PersonnelCertMatcher.PersonnelMatchResult emptyPersonnel =
                new PersonnelCertMatcher.PersonnelMatchResult(List.of());
        BrandAuthMatcher.BrandAuthMatchResult emptyBrand =
                new BrandAuthMatcher.BrandAuthMatchResult(List.of());
        PerformanceMatcher.PerformanceMatchResult emptyPerf =
                new PerformanceMatcher.PerformanceMatchResult(List.of());
        KnowledgeBaseSummary emptySummary = new KnowledgeBaseSummary(0, 0, 0);
        return new KnowledgeBaseMatchResult(
                emptyQual, emptyPersonnel, emptyBrand, emptyPerf, emptySummary);
    }

    private static List<String> combineRequirements(TenderRequirementProfile profile) {
        List<String> all = new ArrayList<>();
        if (profile.qualificationRequirements() != null) all.addAll(profile.qualificationRequirements());
        if (profile.technicalRequirements() != null) all.addAll(profile.technicalRequirements());
        if (profile.commercialRequirements() != null) all.addAll(profile.commercialRequirements());
        return all;
    }
}
