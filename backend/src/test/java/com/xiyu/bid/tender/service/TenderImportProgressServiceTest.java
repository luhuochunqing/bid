package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.tender.dto.TenderImportProgressDTO;
import com.xiyu.bid.tender.dto.TenderImportTaskError;
import com.xiyu.bid.tender.entity.TenderImportTask;
import com.xiyu.bid.tender.repository.TenderImportTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 标讯导入进度服务单元测试。
 *
 * <p>覆盖契约：
 * <ul>
 *   <li>Redis 优先查询：Redis 命中时直接返回，不查 DB</li>
 *   <li>Redis 未命中查 DB：Redis 无值或不可用时，从 DB fallback</li>
 *   <li>clearProgress 清理 Redis 缓存</li>
 *   <li>updateProgress 写入 Redis（含 TTL）</li>
 *   <li>Redis 不可用（Optional.empty()）时优雅降级</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TenderImportProgressServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private TenderImportTaskRepository taskRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TenderImportProgressService service;

    @BeforeEach
    void setUp() {
        service = new TenderImportProgressService(
                Optional.of(redisTemplate), objectMapper, taskRepository);
    }

    @Test
    @DisplayName("getProgress: Redis 命中时优先返回 Redis 数据，不查 DB")
    void getProgress_redisHit_returnsRedisData() throws Exception {
        String taskId = "task-redis-hit";
        TenderImportProgressDTO dto = new TenderImportProgressDTO(
                taskId, "PROCESSING", 100, 50, 45, 5, 50, null, null, null);
        String json = objectMapper.writeValueAsString(dto);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("tender:import:progress:" + taskId)).thenReturn(json);

        TenderImportProgressDTO result = service.getProgress(taskId);

        assertThat(result).isNotNull();
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.status()).isEqualTo("PROCESSING");
        assertThat(result.totalRows()).isEqualTo(100);
        assertThat(result.processedRows()).isEqualTo(50);
        assertThat(result.percent()).isEqualTo(50);
        verify(taskRepository, never()).findByTaskId(any());
    }

    @Test
    @DisplayName("getProgress: Redis 未命中时 fallback 查 DB")
    void getProgress_redisMiss_fallsBackToDb() {
        String taskId = "task-db-fallback";
        TenderImportTask task = TenderImportTask.builder()
                .taskId(taskId)
                .status("COMPLETED")
                .totalRows(20)
                .processedRows(20)
                .successCount(18)
                .failureCount(2)
                .errorDetails(null)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .completedAt(LocalDateTime.now())
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("tender:import:progress:" + taskId)).thenReturn(null);
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));

        TenderImportProgressDTO result = service.getProgress(taskId);

        assertThat(result).isNotNull();
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(20);
        assertThat(result.successCount()).isEqualTo(18);
        assertThat(result.failureCount()).isEqualTo(2);
        assertThat(result.percent()).isEqualTo(100);
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("getProgress: DB 也无记录时返回 null")
    void getProgress_dbEmpty_returnsNull() {
        String taskId = "task-not-exist";

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("tender:import:progress:" + taskId)).thenReturn(null);
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        TenderImportProgressDTO result = service.getProgress(taskId);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getProgress: Redis 不可用时（Optional.empty）优雅降级到 DB")
    void getProgress_redisUnavailable_fallsBackToDb() {
        // 用 Optional.empty() 模拟 Redis 不可用
        TenderImportProgressService noRedisService = new TenderImportProgressService(
                Optional.empty(), objectMapper, taskRepository);

        String taskId = "task-no-redis";
        TenderImportTask task = TenderImportTask.builder()
                .taskId(taskId)
                .status("PENDING")
                .totalRows(0)
                .processedRows(0)
                .successCount(0)
                .failureCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));

        TenderImportProgressDTO result = noRedisService.getProgress(taskId);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("PENDING");
        verify(taskRepository, times(1)).findByTaskId(taskId);
    }

    @Test
    @DisplayName("getProgress: DB 中 error_details JSON 能正确反序列化为 errors 列表")
    void getProgress_dbWithErrors_deserializesErrors() {
        String taskId = "task-with-errors";
        List<TenderImportTaskError> errors = List.of(
                new TenderImportTaskError(2, "duplicate", "标讯已存在", "测试标讯A"),
                new TenderImportTaskError(3, "row", "字段缺失", "测试标讯B"));
        String errorJson;
        try {
            errorJson = objectMapper.writeValueAsString(errors);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        TenderImportTask task = TenderImportTask.builder()
                .taskId(taskId)
                .status("PARTIAL_SUCCESS")
                .totalRows(3)
                .processedRows(3)
                .successCount(1)
                .failureCount(2)
                .errorDetails(errorJson)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("tender:import:progress:" + taskId)).thenReturn(null);
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));

        TenderImportProgressDTO result = service.getProgress(taskId);

        assertThat(result).isNotNull();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).field()).isEqualTo("duplicate");
        assertThat(result.errors().get(1).tenderTitle()).isEqualTo("测试标讯B");
    }

    @Test
    @DisplayName("updateProgress: 写入 Redis（含 TTL）")
    void updateProgress_writesToRedisWithTtl() {
        String taskId = "task-update";
        TenderImportProgressDTO dto = new TenderImportProgressDTO(
                taskId, "PROCESSING", 100, 10, 8, 2, 10, null, null, null);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service.updateProgress(taskId, dto);

        verify(valueOps, times(1)).set(
                eq("tender:import:progress:" + taskId),
                any(String.class),
                any(java.time.Duration.class));
    }

    @Test
    @DisplayName("updateProgress: Redis 不可用时不抛异常（优雅降级）")
    void updateProgress_redisUnavailable_doesNotThrow() {
        TenderImportProgressService noRedisService = new TenderImportProgressService(
                Optional.empty(), objectMapper, taskRepository);
        TenderImportProgressDTO dto = new TenderImportProgressDTO(
                "task-no-redis-update", "PROCESSING", 1, 0, 0, 0, 0, null, null, null);

        // 不抛异常即可
        noRedisService.updateProgress("task-no-redis-update", dto);
    }

    @Test
    @DisplayName("clearProgress: 删除 Redis 缓存 key")
    void clearProgress_deletesRedisKey() {
        String taskId = "task-clear";

        service.clearProgress(taskId);

        verify(redisTemplate, times(1)).delete("tender:import:progress:" + taskId);
    }

    @Test
    @DisplayName("clearProgress: Redis 不可用时不抛异常（优雅降级）")
    void clearProgress_redisUnavailable_doesNotThrow() {
        TenderImportProgressService noRedisService = new TenderImportProgressService(
                Optional.empty(), objectMapper, taskRepository);

        // 不抛异常即可
        noRedisService.clearProgress("task-no-redis-clear");
    }
}
