package com.videofeed.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.videofeed.entity.OutboxEvent;
import com.videofeed.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Relay：轮询 outbox_event 表，把 NEW 状态事件投递到 RocketMQ。
 *
 * 可靠性设计：
 *  1. SELECT ... FOR UPDATE SKIP LOCKED：多实例部署时互不阻塞、不重复消费同一条
 *  2. syncSend 同步等 Broker ACK（SEND_OK 才标 SENT）
 *  3. 失败指数退避重试（2^n 秒，上限 60s），超过 5 次标记 FAILED 人工介入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 5;

    private final OutboxEventMapper outboxMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${app.mq.topic}")
    private String topic;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay() {
        List<OutboxEvent> events = outboxMapper.selectList(Wrappers.<OutboxEvent>lambdaQuery()
                .eq(OutboxEvent::getStatus, OutboxEvent.STATUS_NEW)
                .le(OutboxEvent::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(OutboxEvent::getId)
                .last("LIMIT " + BATCH_SIZE + " FOR UPDATE SKIP LOCKED"));
        for (OutboxEvent event : events) {
            try {
                // destination = topic:tag，tag 用事件类型，消费端可按 tag 过滤
                SendResult result = rocketMQTemplate.syncSend(topic + ":" + event.getEventType(), event.getPayload(), 3000);
                if (result.getSendStatus() != SendStatus.SEND_OK) {
                    throw new IllegalStateException("Broker 返回异常状态: " + result.getSendStatus());
                }
                event.setStatus(OutboxEvent.STATUS_SENT);
                outboxMapper.updateById(event);
            } catch (Exception e) {
                markFailure(event, e);
            }
        }
    }

    private void markFailure(OutboxEvent event, Exception cause) {
        int retryCount = event.getRetryCount() + 1;
        event.setRetryCount(retryCount);
        if (retryCount >= MAX_RETRY) {
            event.setStatus(OutboxEvent.STATUS_FAILED);
            log.error("Outbox 事件投递最终失败 id={} type={}", event.getId(), event.getEventType(), cause);
        } else {
            event.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(1L << retryCount, 60)));
            log.warn("Outbox 投递失败，退避重试 id={} retry={} 下次={} 原因={}",
                    event.getId(), retryCount, event.getNextRetryAt(), cause.getMessage());
        }
        outboxMapper.updateById(event);
    }

    /** 已投递事件保留 1 小时供排查，之后清理 */
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void cleanup() {
        int deleted = outboxMapper.delete(Wrappers.<OutboxEvent>lambdaQuery()
                .eq(OutboxEvent::getStatus, OutboxEvent.STATUS_SENT)
                .lt(OutboxEvent::getUpdatedAt, LocalDateTime.now().minusHours(1)));
        if (deleted > 0) {
            log.info("清理已投递 Outbox 事件 {} 条", deleted);
        }
    }
}
