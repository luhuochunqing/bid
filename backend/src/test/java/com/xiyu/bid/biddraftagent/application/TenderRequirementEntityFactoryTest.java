package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderRequirementEntityFactoryTest {

    private final TenderRequirementEntityFactory factory = new TenderRequirementEntityFactory();

    @Test
    void buildItems_shouldNormalizeOpenAiRequirementShapeBeforePersistence() {
        TenderRequirementProfile profile = new TenderRequirementProfile(
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new TenderRequirementItemSnapshot("TECHNICAL", "技术", "响应技术参数", true, "技术要求", 120),
                        new TenderRequirementItemSnapshot("unexpected", "未知", "模型返回未知分类", false, "其他要求", -5)
                )
        );

        List<BidRequirementItem> items = factory.buildItems(11L, 22L, 33L, profile);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getCategory()).isEqualTo("technical");
        assertThat(items.get(0).getConfidence()).isEqualTo(100);
        assertThat(items.get(1).getCategory()).isEqualTo("other");
        assertThat(items.get(1).getConfidence()).isZero();
    }
}
