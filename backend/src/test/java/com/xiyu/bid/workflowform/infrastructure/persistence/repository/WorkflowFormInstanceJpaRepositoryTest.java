package com.xiyu.bid.workflowform.infrastructure.persistence.repository;

import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;
import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormInstanceEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.profiles.active=test"
})
class WorkflowFormInstanceJpaRepositoryTest {

    @Autowired
    private WorkflowFormInstanceJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void mark_oa_approved_only_updates_rows_still_approving() {
        WorkflowFormInstanceEntity entity = newInstance(WorkflowFormStatus.OA_APPROVING);
        entityManager.persistAndFlush(entity);
        entityManager.clear();

        assertThat(repository.markOaApprovedIfApproving(entity.getId(), "经理", "同意")).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.markOaApprovedIfApproving(entity.getId(), "经理", "重复通过")).isZero();
        WorkflowFormInstanceEntity updated = entityManager.find(WorkflowFormInstanceEntity.class, entity.getId());
        assertThat(updated.getStatus()).isEqualTo(WorkflowFormStatus.OA_APPROVED);
        assertThat(updated.getOaComment()).isEqualTo("同意");
    }

    @Test
    void mark_oa_approved_does_not_update_rejected_instance() {
        WorkflowFormInstanceEntity entity = newInstance(WorkflowFormStatus.OA_REJECTED);
        entityManager.persistAndFlush(entity);
        entityManager.clear();

        assertThat(repository.markOaApprovedIfApproving(entity.getId(), "经理", "同意")).isZero();

        WorkflowFormInstanceEntity unchanged = entityManager.find(WorkflowFormInstanceEntity.class, entity.getId());
        assertThat(unchanged.getStatus()).isEqualTo(WorkflowFormStatus.OA_REJECTED);
    }

    private static WorkflowFormInstanceEntity newInstance(WorkflowFormStatus status) {
        WorkflowFormInstanceEntity entity = new WorkflowFormInstanceEntity();
        entity.setBusinessType(FormBusinessType.QUALIFICATION_BORROW);
        entity.setTemplateCode("QUALIFICATION_BORROW");
        entity.setProjectId(10L);
        entity.setApplicantName("小王");
        entity.setStatus(status);
        entity.setFormDataJson("{}");
        entity.setOaInstanceId("OA-1");
        entity.setBusinessApplied(false);
        return entity;
    }
}
