// Input: projectId → BidTenderDocumentSnapshot（profile_json）→ TechnicalSubTypePolicy
// Output: 按四类标签分类的技术要点列表
// Pos: biddraftagent/application — 技术要点分类应用服务

package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.technical.TechnicalRequirementItem;
import com.xiyu.bid.biddraftagent.domain.technical.TechnicalSubTypePolicy;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 技术要点分类应用服务。
 * 将招标文件中的技术要求按四类标签分类：硬指标/功能/兼容性/加分项。
 */
@Service
@RequiredArgsConstructor
public class TechnicalClassificationAppService {

    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final BidDraftAgentJsonCodec jsonCodec;
    private final TechnicalSubTypePolicy classificationPolicy = new TechnicalSubTypePolicy();

    /**
     * 根据项目 ID 加载最新招标文件解析结果中的技术要求，返回分类结果。
     */
    public TechnicalClassificationResult classifyForProject(Long projectId) {
        var snapshot = snapshotRepository
                .findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .orElse(null);
        if (snapshot == null || snapshot.getProfileJson() == null) {
            return new TechnicalClassificationResult(List.of());
        }
        TenderRequirementProfile profile = jsonCodec.fromJson(
                snapshot.getProfileJson(), TenderRequirementProfile.class);
        if (profile == null || profile.technicalRequirements() == null) {
            return new TechnicalClassificationResult(List.of());
        }
        List<TechnicalRequirementItem> items = classificationPolicy.classifyAll(profile.technicalRequirements());
        return new TechnicalClassificationResult(items);
    }

    public record TechnicalClassificationResult(List<TechnicalRequirementItem> items) {}
}
