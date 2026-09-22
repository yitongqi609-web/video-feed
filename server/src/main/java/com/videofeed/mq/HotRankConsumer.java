package com.videofeed.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videofeed.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 热榜消费者：消费互动事件（点赞/取消点赞/评论），ZINCRBY 增量更新热度。
 * Outbox 至少一次投递 + 消费幂等性由「增量更新可重放」保证：
 * 重复消费同一事件只是多加一次热度，最终由周期全量重建校准。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "${app.mq.topic}", consumerGroup = "hot-rank-consumer")
public class HotRankConsumer implements RocketMQListener<String> {

    private final HotRankService hotRankService;
    private final ObjectMapper objectMapper;

    @Value("${app.hot.like-weight}")
    private int likeWeight;
    @Value("${app.hot.comment-weight}")
    private int commentWeight;

    @Override
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            long videoId = node.get("videoId").asLong();
            String type = node.get("type").asText();
            int delta = switch (type) {
                case "LIKE" -> likeWeight;
                case "UNLIKE" -> -likeWeight;
                case "COMMENT" -> commentWeight;
                default -> 0;
            };
            if (delta != 0) {
                hotRankService.increase(videoId, delta);
                log.debug("热度增量 videoId={} delta={} payload={}", videoId, delta, payload);
            }
        } catch (Exception e) {
            log.error("热榜消息处理失败，消息将重试: {}", payload, e);
            throw new IllegalArgumentException(e); // 抛出让 MQ 重试
        }
    }
}
