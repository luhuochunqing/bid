package com.xiyu.bid.personnel.infrastructure.persistence;

import com.xiyu.bid.personnel.application.service.PersonnelOperationLogService;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.support.AbstractMysqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-469 第五轮根因：批量导入/导出操作日志 personnel_id 为 NULL 时写入失败。
 * V1134 迁移允许 personnel_id 为 NULL；本测试验证该契约在真实 MySQL 下生效。
 */
@SpringBootTest
@ActiveProfiles("flyway-mysql")
class PersonnelOperationLogNullPersonnelIdMysqlIntegrationTest extends AbstractMysqlIntegrationTest {

    @Autowired
    private PersonnelOperationLogService operationLogService;

    @Test
    void 批量导出操作日志允许personnelId为空() {
        PersonnelOperationLog saved = operationLogService.save(PersonnelOperationLog.create(
                null,
                42L,
                "测试操作人",
                PersonnelOperationLog.OperationType.BATCH_EXPORT_PERSONNEL,
                List.of(new PersonnelOperationLog.ChangeDetail("recordCount", "3", ""))
        ));

        Optional<PersonnelOperationLog> found = operationLogService.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().personnelId()).isNull();
        assertThat(found.get().operationType()).isEqualTo("BATCH_EXPORT_PERSONNEL");
    }

    @Test
    void 批量导入操作日志允许personnelId为空() {
        PersonnelOperationLog saved = operationLogService.save(PersonnelOperationLog.create(
                null,
                42L,
                "测试操作人",
                PersonnelOperationLog.OperationType.BATCH_IMPORT_PERSONNEL,
                List.of(
                        new PersonnelOperationLog.ChangeDetail("total", "10", ""),
                        new PersonnelOperationLog.ChangeDetail("success", "8", ""),
                        new PersonnelOperationLog.ChangeDetail("failure", "2", "")
                )
        ));

        Optional<PersonnelOperationLog> found = operationLogService.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().personnelId()).isNull();
        assertThat(found.get().operationType()).isEqualTo("BATCH_IMPORT_PERSONNEL");
    }

    @Test
    void 单条人员操作日志仍可正常写入() {
        PersonnelOperationLog saved = operationLogService.save(PersonnelOperationLog.create(
                123L,
                42L,
                "测试操作人",
                PersonnelOperationLog.OperationType.CREATE,
                List.of(new PersonnelOperationLog.ChangeDetail("name", "", "张三"))
        ));

        Optional<PersonnelOperationLog> found = operationLogService.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().personnelId()).isEqualTo(123L);
        assertThat(found.get().operationType()).isEqualTo("CREATE");
    }
}
