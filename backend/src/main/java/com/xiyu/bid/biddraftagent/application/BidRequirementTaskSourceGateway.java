package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.projectworkflow.core.TaskBreakdownPolicy;
import com.xiyu.bid.projectworkflow.service.ProjectTaskRequirementSourceGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BidRequirementTaskSourceGateway implements ProjectTaskRequirementSourceGateway {

    private final BidRequirementSnapshotReader requirementSnapshotReader;

    @Override
    public List<TaskBreakdownPolicy.SourceSnapshot> latestRequirementSourcesForProject(Long projectId) {
        return requirementSnapshotReader.latestRequirementsForProject(projectId).stream()
                .map(item -> new TaskBreakdownPolicy.SourceSnapshot(
                        item.getCategory(),
                        item.getTitle(),
                        selectRequirementContent(item)
                ))
                .toList();
    }

    private String selectRequirementContent(BidRequirementItem item) {
        if (item.getContent() != null && !item.getContent().isBlank()) {
            return item.getContent();
        }
        return item.getSourceExcerpt();
    }
}
