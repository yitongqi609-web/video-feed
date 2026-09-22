package com.videofeed.id;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 精简版 Twitter Snowflake：
 *   41bit 时间戳（自定义纪元 2025-01-01） | 5bit workerId | 12bit 序列
 * ID 趋势递增，天然适合作为游标分页的 cursor。
 *
 * 时钟回拨处理：小回拨（<=5ms）等待时钟追上；大回拨直接拒绝发号，避免发出重复/乱序 ID。
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1735689600000L; // 2025-01-01 UTC
    private static final long WORKER_BITS = 5;
    private static final long SEQUENCE_BITS = 12;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long TIMESTAMP_SHIFT = WORKER_BITS + SEQUENCE_BITS;
    private static final long MAX_BACKWARD_MS = 5;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(@Value("${app.snowflake.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 0~" + MAX_WORKER_ID + " 之间");
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastTimestamp) {
            long backward = lastTimestamp - now;
            if (backward <= MAX_BACKWARD_MS) {
                try {
                    this.wait(backward + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("发号等待被中断", e);
                }
                now = System.currentTimeMillis();
            }
            if (now < lastTimestamp) {
                throw new IllegalStateException("时钟回拨 %dms，拒绝发号".formatted(backward));
            }
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) { // 当前毫秒 4096 个号耗尽，自旋到下一毫秒
                while ((now = System.currentTimeMillis()) <= lastTimestamp) {
                    Thread.onSpinWait();
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << TIMESTAMP_SHIFT) | (workerId << SEQUENCE_BITS) | sequence;
    }
}
