package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.tender.dto.TenderImportProgressDTO;
import com.xiyu.bid.tender.dto.TenderImportTaskError;
import com.xiyu.bid.tender.entity.TenderImportTask;
import com.xiyu.bid.tender.repository.TenderImportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 标讯导入进度服务。
 * <p>Redis 优先 + DB fallback，参考 PersonnelImportProgressService 的
 * Optional<StringRedisTemplate> 注入模式（CO-469 第三轮修复范式）。
 * <p>Redis 不可用时通过 Optional.empty() 优雅降级，不阻断导入流程。
 *
 * @see com.xiyu.bid.personnel.application.service.PersonnelImportProgressService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderImportProgressService {

    private static final String REDIS_KEY_PREFIX = "tender:import:progress:";
    private static final Duration REDIS_TTL = Duration.ofDays(7);

    private final Optional<StringRedisTemplate> redisTemplate;
    private final ObjectMapper objectMapper;
    private final TenderImportTaskRepository taskRepository;

    /** 更新进度到 Redis（高频调用，不查 DB） */
    public void updateProgress(String taskId, TenderImportProgressDTO progress) {
        String key = REDIS_KEY_PREFIX + taskId;
        try {
            setRedisValue(key, objectMapper.writeValueAsString(progress), REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("更新标讯导入进度失败: taskId={}", taskId, e);
        }
    }

    /** 查询进度：Redis 优先，未命中查 DB */
    public TenderImportProgressDTO getProgress(String taskId) {
        String key = REDIS_KEY_PREFIX + taskId;
        Optional<String> progressJson = getRedisValue(key);
        if (progressJson.isPresent()) {
            try {
                return objectMapper.readValue(progressJson.get(), TenderImportProgressDTO.class);
            } catch (JsonProcessingException e) {
                log.warn("解析标讯导入进度 JSON 失败: taskId={}", taskId, e);
            }
        }
        // DB fallback：Redis 未命中或不可用时，从数据库读取任务状态
        return taskRepository.findByTaskId(taskId)
                .map(this::toProgressDTO)
                .orElse(null);
    }

    /** 清除 Redis 进度缓存（任务完成 24h 后调用） */
    public void clearProgress(String taskId) {
        String key = REDIS_KEY_PREFIX + taskId;
        redisTemplate.ifPresent(template -> {
            try {
                template.delete(key);
            } catch (RuntimeException e) {
                log.debug("Redis unavailable, skip deleting key={}: {}", key, e.getMessage());
            }
        });
    }

    private TenderImportProgressDTO toProgressDTO(TenderImportTask task) {
        List<TenderImportTaskError> errors = deserializeErrors(task.getErrorDetails());
        int percent = task.getTotalRows() > 0
                ? (int) (100L * task.getProcessedRows() / task.getTotalRows())
                : 0;
        return new TenderImportProgressDTO(
                task.getTaskId(),
                task.getStatus(),
                task.getTotalRows(),
                task.getProcessedRows(),
                task.getSuccessCount(),
                task.getFailureCount(),
                percent,
                errors,
                task.getCreatedAt(),
                task.getCompletedAt()
        );
    }

    private List<TenderImportTaskError> deserializeErrors(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, TenderImportTaskError.class));
        } catch (JsonProcessingException e) {
            log.warn("解析 error_details JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private Optional<String> getRedisValue(String key) {
        return redisTemplate.flatMap(template -> {
            try {
                String value = template.opsForValue().get(key);
                return value != null ? Optional.of(value) : Optional.empty();
            } catch (RuntimeException e) {
                log.debug("Redis unavailable, skip reading key={}: {}", key, e.getMessage());
                return Optional.empty();
            }
        });
    }

    private void setRedisValue(String key, String value, Duration ttl) {
        redisTemplate.ifPresent(template -> {
            try {
                template.opsForValue().set(key, value, ttl);
            } catch (RuntimeException e) {
                log.debug("Redis unavailable, skip writing key={}: {}", key, e.getMessage());
            }
        });
    }
}
