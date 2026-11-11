// Input: sub_topic 仍为空的 knowledge_case 行。
// Output: 仅条件更新子主题和质量分，保留并发业务写入；返回实际更新条数。
// Pos: casework 持久化适配器；计分规则在 KnowledgeCaseFeatures。
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.application.port.KnowledgeCaseFeatureBackfillPort;
import com.xiyu.bid.casework.domain.policy.KnowledgeCaseFeatures;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KnowledgeCaseFeatureBackfillAdapter implements KnowledgeCaseFeatureBackfillPort {

    private final KnowledgeCaseRepository knowledgeCaseRepository;

    public KnowledgeCaseFeatureBackfillAdapter(KnowledgeCaseRepository knowledgeCaseRepository) {
        this.knowledgeCaseRepository = knowledgeCaseRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int backfillMissingSubTopic() {
        List<KnowledgeCase> pending = knowledgeCaseRepository.findBySubTopicIsNull();
        int updated = 0;
        for (KnowledgeCase row : pending) {
            KnowledgeCaseFeatures.Assigned features = KnowledgeCaseFeatures.assign(
                    row.getScoringPointTitle(), row.getResponseText());
            updated += knowledgeCaseRepository.updateFeaturesIfUnchanged(
                    row.getId(), features.subTopic(), features.qualityScore(),
                    row.getScoringPointTitle(), row.getResponseText(), row.getQualityScore());
        }
        return updated;
    }
}
