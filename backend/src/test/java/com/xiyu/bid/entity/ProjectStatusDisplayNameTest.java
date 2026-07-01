package com.xiyu.bid.entity;

import com.xiyu.bid.entity.Project.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStatusDisplayNameTest {

    @Test
    void allStatus_displayName_returnsChinese() {
        assertThat(Status.PENDING_INITIATION.displayName()).isEqualTo("待立项");
        assertThat(Status.INITIATED.displayName()).isEqualTo("已立项");
        assertThat(Status.BIDDING.displayName()).isEqualTo("投标中");
        assertThat(Status.EVALUATING.displayName()).isEqualTo("评标中");
        assertThat(Status.WON.displayName()).isEqualTo("已中标");
        assertThat(Status.LOST.displayName()).isEqualTo("未中标");
        assertThat(Status.FAILED.displayName()).isEqualTo("已流标");
        assertThat(Status.ABANDONED.displayName()).isEqualTo("已放弃");
    }

    @Test
    void status_name_differsFrom_displayName() {
        assertThat(Status.WON.name()).isEqualTo("WON");
        assertThat(Status.WON.displayName()).isEqualTo("已中标");
        assertThat(Status.BIDDING.name()).isEqualTo("BIDDING");
        assertThat(Status.BIDDING.displayName()).isEqualTo("投标中");
    }
}
