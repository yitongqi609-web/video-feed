package com.videofeed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Transactional Outbox：与业务写入同一个本地事务，保证「业务变更 + 事件记录」原子落库，
 * 由 OutboxRelay 异步投递 RocketMQ，实现至少一次投递 + 失败退避重试
 */
@Data
@TableName("outbox_event")
public class OutboxEvent {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventType;

    private Long aggregateId;

    private String payload;

    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
