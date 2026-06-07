package com.xiyu.bid.task.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDeliverableAssemblerTest {

    @Test
    void toEntityStoresUploadedFileUrlAsStoragePath() {
        TaskDeliverableCreateRequest request = TaskDeliverableCreateRequest.builder()
                .name("技术方案.docx")
                .deliverableType("TECHNICAL")
                .url("project-documents://12/tasks/31/技术方案.docx")
                .build();

        var entity = TaskDeliverableAssembler.toEntity(request, 31L, 1, 9L, "测试用户");

        assertThat(entity.getStoragePath()).isEqualTo("project-documents://12/tasks/31/技术方案.docx");
        assertThat(TaskDeliverableAssembler.toDTO(entity).getUrl())
                .isEqualTo("project-documents://12/tasks/31/技术方案.docx");
    }
}
