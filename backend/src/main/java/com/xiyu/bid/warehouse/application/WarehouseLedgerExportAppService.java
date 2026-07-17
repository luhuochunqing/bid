package com.xiyu.bid.warehouse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.warehouse.domain.WarehouseLedgerExportPolicy.Section;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仓库台账导出应用服务 — 19 列精简版（无附件、无系统字段）。
 * 复用 warehouse_export_task 表与异步/通知/下载链路。
 *
 * <p><b>事务边界设计（P1-1 修复）</b>：本类不再标注 {@code @Transactional}。
 * createTask 由 {@link WarehouseExportTaskStateService} 以独立事务执行并提交，
 * 避免原 {@code @Async} + {@code @Transactional} 竞态。
 *
 * <p><b>self-invocation 修复</b>：@Async 方法已提取到
 * {@link WarehouseLedgerExportAsyncExecutor}，通过依赖注入调用使 @Async 代理生效。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseLedgerExportAppService {

    private final WarehouseLedgerExportAsyncExecutor asyncExecutor;
    private final WarehouseExportTaskStateService stateService;
    private final ObjectMapper objectMapper;

    public record ExportRequest(String scope, List<Long> ids, WarehouseFilterDTO filter, Set<Section> sections) {}

    /**
     * 创建台账导出任务，触发异步执行。
     * <p>无 @Transactional：stateService.createTask 以独立事务提交后立即返回 taskId，
     * 异步线程可立即查询到，避免 @Async + @Transactional 竞态。
     */
    public WarehouseExportAppService.ExportTaskResult trigger(ExportRequest req, Long operatorId, String operatorUsername) {
        String filterSnapshot = serialize(req);
        Long taskId = stateService.createTask(filterSnapshot, operatorId);
        asyncExecutor.executeLedger(taskId, req, operatorId, operatorUsername, System.currentTimeMillis());
        return new WarehouseExportAppService.ExportTaskResult(taskId);
    }

    private String serialize(ExportRequest req) {
        Map<String, Object> map = new HashMap<>();
        map.put("format", "ledger");
        map.put("scope", req.scope());
        map.put("ids", req.ids());
        map.put("sections", req.sections());
        map.put("filter", req.filter());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
