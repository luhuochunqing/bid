package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.projectworkflow.core.TaskBreakdownPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BidRequirementTaskSourceGatewayTest {

    @Test
    void latestRequirementSourcesForProject_ShouldMapRequirementItemsToTaskSourceSnapshots() {
        BidRequirementSnapshotReader snapshotReader = mock(BidRequirementSnapshotReader.class);
        when(snapshotReader.latestRequirementsForProject(1001L)).thenReturn(List.of(
                requirement("commercial", "商务响应", "商务条款正文", "商务摘录"),
                requirement("qualification", "资质材料", " ", "资质摘录")
        ));
        BidRequirementTaskSourceGateway gateway = new BidRequirementTaskSourceGateway(snapshotReader);

        List<TaskBreakdownPolicy.SourceSnapshot> sources = gateway.latestRequirementSourcesForProject(1001L);

        assertThat(sources).containsExactly(
                new TaskBreakdownPolicy.SourceSnapshot("commercial", "商务响应", "商务条款正文"),
                new TaskBreakdownPolicy.SourceSnapshot("qualification", "资质材料", "资质摘录")
        );
    }

    private BidRequirementItem requirement(String category, String title, String content, String sourceExcerpt) {
        return BidRequirementItem.builder()
                .category(category)
                .title(title)
                .content(content)
                .sourceExcerpt(sourceExcerpt)
                .build();
    }
}
