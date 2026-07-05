package com.xiyu.bid.personnel.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.personnel.domain.model.importtask.ImportErrorDetail;
import com.xiyu.bid.personnel.domain.model.importtask.ImportTaskStatus;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelImportTaskEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelImportTaskJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-469 第八轮防复发测试：
 * 历史根因是 {@code serializeErrorDetails} 用 {@code List.toString()} 输出非合法 JSON，
 * 导致 MySQL cast(? as json) 失败，failImportTask 自身抛 DataIntegrityViolationException，
 * 任务状态永久停在 PROCESSING/5%。
 *
 * 这些测试断言序列化结果一定是合法 JSON，无论 errorMessage 包含什么特殊字符。
 */
class PersonnelImportTaskRepositoryAdapterTest {

    private PersonnelImportTaskJpaRepository jpaRepository;
    private PersonnelImportTaskRepositoryAdapter adapter;
    private final ObjectMapper validator = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PersonnelImportTaskJpaRepository.class);
        adapter = new PersonnelImportTaskRepositoryAdapter(jpaRepository);
    }

    @Test
    void save_当errorDetails为空时_errorDetails字段应为合法JSON空数组() {
        PersonnelImportTask task = buildTask(List.of());

        when(jpaRepository.save(any(PersonnelImportTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adapter.save(task);

        ArgumentCaptor<PersonnelImportTaskEntity> captor =
                ArgumentCaptor.forClass(PersonnelImportTaskEntity.class);
        verify(jpaRepository).save(captor.capture());

        String errorDetails = captor.getValue().getErrorDetails();
        assertThat(errorDetails).isEqualTo("[]");
        // 防复发核心断言：能被 Jackson 解析为合法 JSON（等价于 MySQL JSON 校验）
        assertThatNoException().isThrownBy(() -> validator.readTree(errorDetails));
    }

    @Test
    void save_当errorDetails含特殊字符时_errorDetails字段应为合法JSON() {
        // 模拟 Java 异常 message 中常见特殊字符：双引号、换行、反斜杠
        ImportErrorDetail detail = new ImportErrorDetail(
                "系统", null, null, null,
                "IOException: \"stream\" closed\n\tat com.xiyu.foo.Bar\\baz"
        );
        PersonnelImportTask task = buildTask(List.of(detail));

        when(jpaRepository.save(any(PersonnelImportTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adapter.save(task);

        ArgumentCaptor<PersonnelImportTaskEntity> captor =
                ArgumentCaptor.forClass(PersonnelImportTaskEntity.class);
        verify(jpaRepository).save(captor.capture());

        String errorDetails = captor.getValue().getErrorDetails();
        // 防复发核心断言：必须是合法 JSON（不能是 List.toString() 的 [ImportErrorDetail[...]] 格式）
        assertThatNoException().isThrownBy(() -> validator.readTree(errorDetails));
        assertThat(errorDetails).startsWith("[");
        assertThat(errorDetails).contains("\"sheetName\":\"系统\"");
        assertThat(errorDetails).contains("\"errorMessage\":\"IOException: \\\"stream\\\" closed");
    }

    @Test
    void save_当errorDetails含多条记录时_errorDetails字段应为合法JSON数组() {
        ImportErrorDetail d1 = new ImportErrorDetail("基础信息", 5, "EMP001", "张三", "姓名必填");
        ImportErrorDetail d2 = new ImportErrorDetail("证书与职称", 12, null, null, "证书编号重复");
        PersonnelImportTask task = buildTask(List.of(d1, d2));

        when(jpaRepository.save(any(PersonnelImportTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adapter.save(task);

        ArgumentCaptor<PersonnelImportTaskEntity> captor =
                ArgumentCaptor.forClass(PersonnelImportTaskEntity.class);
        verify(jpaRepository).save(captor.capture());

        String errorDetails = captor.getValue().getErrorDetails();
        assertThatNoException().isThrownBy(() -> validator.readTree(errorDetails));
        assertThat(errorDetails).startsWith("[");
        assertThat(errorDetails).endsWith("]");
        // 必须包含 2 个 JSON 对象
        long objectCount = errorDetails.chars().filter(c -> c == '{').count();
        assertThat(objectCount).isEqualTo(2);
    }

    private PersonnelImportTask buildTask(List<ImportErrorDetail> errorDetails) {
        return new PersonnelImportTask(
                1L, "IMP-PER-TEST-001", "PERSONNEL", ImportTaskStatus.PENDING,
                0, 0, 0, 0, errorDetails, null, 1L,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }
}
