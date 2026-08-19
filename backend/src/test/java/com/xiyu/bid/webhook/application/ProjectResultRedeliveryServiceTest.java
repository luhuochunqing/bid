// Input: ProjectResultRedeliveryService 纯函数
// Output: JUnit 5 单测（事件重建 / CSV 解析 / 操作人格式化）
// Pos: webhook/application/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.entity.ProjectResultCompetitor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectResultRedeliveryServiceTest {

    private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 8, 16, 14, 0, 18);

    private static ProjectResult sampleResult() {
        return ProjectResult.builder()
                .id(114L)
                .projectId(225L)
                .resultType("WON")
                .summary("")
                .evidenceDocIds("992")
                .createdBy(587L)
                .registeredAt(REGISTERED_AT)
                .createdAt(REGISTERED_AT)
                .build();
    }

    private static Project sampleProject() {
        return Project.builder().id(225L).tenderId(1695L).build();
    }

    @Test
    void rebuildEvent_restoresAllFieldsFromDbEntities() {
        User operator = User.builder().fullName("张三").employeeNumber("03595").username("zhangsan").build();
        ProjectResultCompetitor competitor = ProjectResultCompetitor.builder()
                .resultId(114L).name("友商A").discount("5%").paymentTerm("30天").notes("").sortOrder(0)
                .build();

        ProjectResultConfirmedEvent event = ProjectResultRedeliveryService.rebuildEvent(
                sampleResult(), sampleProject(), List.of(competitor), operator);

        assertThat(event.projectId()).isEqualTo(225L);
        assertThat(event.tenderId()).isEqualTo(1695L);
        assertThat(event.resultType()).isEqualTo(BidResultType.WON);
        assertThat(event.evidenceFileIds()).containsExactly(992L);
        assertThat(event.operatorUserId()).isEqualTo(587L);
        assertThat(event.operatorName()).isEqualTo("张三（03595）");
        assertThat(event.resultId()).isEqualTo(114L);
        // occurredAt 必须取原登记时间，保证 businessKey/operateTime 与历史一致
        assertThat(event.occurredAt()).isEqualTo(REGISTERED_AT);
        assertThat(event.competitors()).hasSize(1);
        assertThat(event.competitors().get(0).name()).isEqualTo("友商A");
        assertThat(event.competitors().get(0).discount()).isEqualTo("5%");
    }

    @Test
    void rebuildEvent_nullOperator_fallsBackToUnknown() {
        ProjectResultConfirmedEvent event = ProjectResultRedeliveryService.rebuildEvent(
                sampleResult(), sampleProject(), List.of(), null);

        assertThat(event.operatorName()).isEqualTo("unknown");
        assertThat(event.evidenceFileIds()).containsExactly(992L);
    }

    @Test
    void rebuildEvent_registeredAtNull_fallsBackToCreatedAt() {
        ProjectResult result = sampleResult();
        result.setRegisteredAt(null);

        ProjectResultConfirmedEvent event = ProjectResultRedeliveryService.rebuildEvent(
                result, sampleProject(), null, null);

        assertThat(event.occurredAt()).isEqualTo(REGISTERED_AT);
        assertThat(event.competitors()).isEmpty();
    }

    @Test
    void parseCsv_multiIdsBlankAndDirtyInputs() {
        assertThat(ProjectResultRedeliveryService.parseCsv("987, 992")).containsExactly(987L, 992L);
        assertThat(ProjectResultRedeliveryService.parseCsv(null)).isEmpty();
        assertThat(ProjectResultRedeliveryService.parseCsv("  ")).isEmpty();
        // 脏数据不打挂重推路径
        assertThat(ProjectResultRedeliveryService.parseCsv("abc,def")).isEmpty();
    }

    @Test
    void formatOperatorName_variants() {
        assertThat(ProjectResultRedeliveryService.formatOperatorName(null)).isEqualTo("unknown");
        // getDisplayEmployeeNumber 在 employeeNumber 为空时回退 username
        User noNumber = User.builder().fullName("李四").username("lisi").build();
        assertThat(ProjectResultRedeliveryService.formatOperatorName(noNumber)).isEqualTo("李四（lisi）");
        User noName = User.builder().employeeNumber("03595").username("u1").build();
        assertThat(ProjectResultRedeliveryService.formatOperatorName(noName)).isEqualTo("03595");
    }
}
