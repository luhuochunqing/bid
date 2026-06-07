// Input: projectId → BidTenderDocumentSnapshot（profile_json）→ KnowledgeBaseDataLoader
// Output: QualificationMatchResult（三态匹配结果）
// Pos: biddraftagent/application — 资质匹配应用服务（imperative shell）

package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationMatchResult;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationMatcher;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 资质要求识别应用服务。
 * 将招标文件中的资质要求与企业知识库（资质库）进行逐项比对，
 * 输出三态结果：已满足/需关注/不满足。
 */
@Service
@RequiredArgsConstructor
public class QualificationMatchAppService {

    private final KnowledgeBaseDataLoader dataLoader;
    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final BidDraftAgentJsonCodec jsonCodec;

    /**
     * 对提取的资质要求进行匹配，返回逐条三态结果。
     *
     * @param profile AI 提取的招标文件要求画像
     * @return 逐条资质匹配结果
     */
    public QualificationMatchResult match(TenderRequirementProfile profile) {
        return doMatch(profile, LocalDate.now());
    }

    /**
     * 根据项目 ID 加载最新招标文件解析结果中的资质要求，返回匹配结果。
     *
     * @param projectId 项目 ID
     * @return 逐条资质匹配结果；如未上传招标文件则返回空结果
     */
    public QualificationMatchResult matchForProject(Long projectId) {
        var snapshot = snapshotRepository
                .findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .orElse(null);
        if (snapshot == null || snapshot.getProfileJson() == null) {
            return new QualificationMatchResult(List.of());
        }
        TenderRequirementProfile profile = jsonCodec.fromJson(
                snapshot.getProfileJson(), TenderRequirementProfile.class);
        if (profile == null) {
            return new QualificationMatchResult(List.of());
        }
        return doMatch(profile, LocalDate.now());
    }

    private QualificationMatchResult doMatch(TenderRequirementProfile profile, LocalDate today) {
        List<String> requirements = profile.qualificationRequirements();
        var qualifications = dataLoader.loadAll().qualifications();
        return new QualificationMatcher().match(requirements, qualifications, today);
    }
}
