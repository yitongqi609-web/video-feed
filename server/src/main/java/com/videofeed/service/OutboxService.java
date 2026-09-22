package com.videofeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videofeed.entity.OutboxEvent;
import com.videofeed.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Transactional Outbox 写入端：与业务变更在同一个 @Transactional 方法内调用，
 * 「业务数据 + 事件」原子落库。投递由 OutboxRelay 异步完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public void append(String eventType, long aggregateId, Map<String, Object> payload) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setEventType(eventType);
            event.setAggregateId(aggregateId);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setStatus(OutboxEvent.STATUS_NEW);
            event.setRetryCount(0);
            outboxMapper.insert(event);
        } catch (Exception e) {
            throw new IllegalStateException("Outbox 事件写入失败", e);
        }
    }
}
