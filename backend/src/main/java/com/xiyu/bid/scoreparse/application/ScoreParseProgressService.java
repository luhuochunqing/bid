package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * AI 评分解析/打分进度服务（spec 041）。
 * <p>Redis 优先 + DB fallback，参考 TenderImportProgressService 的
 * Optional&lt;StringRedisTemplate&gt; 注入模式（优雅降级，不阻断解析流程）。
 * <p>key: {@code score:{parse|scoring}:progress:{taskId}}，TTL 7d。
 *
 * @see com.xiyu.bid.tender.service.TenderImportProgressService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreParseProgressService {

    private static final Duration REDIS_TTL = Duration.ofDays(7);

    private final Optional<StringRedisTemplate> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ScoreParseTaskRepository taskRepository;

    /** 更新进度到 Redis（高频调用，不查 DB） */
    public void updateProgress(String taskId, ScoreParseProgressDTO progress) {
        String key = redisKey(progress.taskId() == null ? taskId : progress.taskId());
        try {
            setRedisValue(key, objectMapper.writeValueAsString(progress), REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("更新评分任务进度失败: taskId={}", taskId, e);
        }
    }

    /** 查询进度：Redis 优先，未命中查 DB */
    public ScoreParseProgressDTO getProgress(String taskId) {
        Optional<String> progressJson = getRedisValue(redisKey(taskId));
        if (progressJson.isPresent()) {
            try {
                return objectMapper.readValue(progressJson.get(), ScoreParseProgressDTO.class);
            } catch (JsonProcessingException e) {
                log.warn("解析评分任务进度 JSON 失败: taskId={}", taskId, e);
            }
        }
        return taskRepository.findByTaskId(taskId)
                .map(this::toProgressDTO)
                .orElse(null);
    }

    /** 清除 Redis 进度缓存（任务终态后调用） */
    public void clearProgress(String taskId) {
        String key = redisKey(taskId);
        redisTemplate.ifPresent(template -> {
            try {
                template.delete(key);
            } catch (RuntimeException e) {
                log.debug("Redis unavailable, skip deleting key={}: {}", key, e.getMessage());
            }
        });
    }

    /** key 前缀按 taskType 区分：PARSE / SCORING */
    private String redisKey(String taskId) {
        return "score:parse:progress:" + taskId;
    }

    private ScoreParseProgressDTO toProgressDTO(ScoreParseTask task) {
        return new ScoreParseProgressDTO(
                task.getTaskId(),
                task.getStatus(),
                task.getProgress(),
                task.getStage(),
                task.getErrorMessage(),
                task.getStartedAt(),
                task.getCompletedAt()
        );
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
