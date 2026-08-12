package com.xiyu.bid.integration.tenderevent.infrastructure.persistence;

import com.xiyu.bid.integration.tenderevent.application.TenderEventLogPort;
import com.xiyu.bid.integration.tenderevent.application.TenderEventLogRecord;
import com.xiyu.bid.integration.tenderevent.infrastructure.persistence.entity.TenderEventLogEntity;
import com.xiyu.bid.integration.tenderevent.infrastructure.persistence.repository.TenderEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 标讯事件流水记录实现（基础设施层）。
 *
 * <p>把编排层产出的事件推送结果持久化到 {@code tender_event_logs} 表。
 */
@RequiredArgsConstructor
@Slf4j
public class TenderEventLogWriter implements TenderEventLogPort {

    private final TenderEventLogRepository repository;

    @Override
    public void record(TenderEventLogRecord logRecord) {
        TenderEventLogEntity entity = new TenderEventLogEntity();
        entity.setTenderId(logRecord.tenderId());
        entity.setEventCode(logRecord.eventCode());
        entity.setEventSource(logRecord.eventSource());
        entity.setEventTopic(logRecord.eventTopic());
        entity.setTraceId(logRecord.traceId());
        entity.setSpanId(logRecord.spanId());
        entity.setParentId(logRecord.parentId());
        entity.setEventContent(logRecord.eventContent());
        entity.setStatus(logRecord.status());
        entity.setErrorMessage(logRecord.errorMessage());
        repository.save(entity);
    }
}