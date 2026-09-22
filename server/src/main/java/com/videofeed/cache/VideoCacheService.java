package com.videofeed.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videofeed.dto.VideoVO;
import com.videofeed.entity.User;
import com.videofeed.entity.Video;
import com.videofeed.mapper.UserMapper;
import com.videofeed.mapper.VideoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 视频详情 Cache-Aside 缓存，三件套防护：
 *  1. 随机 TTL        —— 防雪崩（大量 key 同时到期）
 *  2. 空值缓存        —— 防穿透（不存在的 id 打到数据库）
 *  3. Singleflight   —— 防击穿（热点 key 失效瞬间合并回源）
 *
 * 一致性：点赞/评论等变更在事务提交后删除对应缓存（见 TxUtils.afterCommit），下次读再回填。
 */
@Slf4j
@Service
public class VideoCacheService {

    public static final String KEY_PREFIX = "video:";
    public static final String NULL_MARKER = "__NULL__";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final Singleflight singleflight;

    private final long videoTtlSeconds;
    private final long randomTtlSeconds;
    private final long nullTtlSeconds;

    public VideoCacheService(StringRedisTemplate redis, ObjectMapper objectMapper,
                             VideoMapper videoMapper, UserMapper userMapper, Singleflight singleflight,
                             @Value("${app.cache.video-ttl-seconds}") long videoTtlSeconds,
                             @Value("${app.cache.random-ttl-seconds}") long randomTtlSeconds,
                             @Value("${app.cache.null-ttl-seconds}") long nullTtlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.videoMapper = videoMapper;
        this.userMapper = userMapper;
        this.singleflight = singleflight;
        this.videoTtlSeconds = videoTtlSeconds;
        this.randomTtlSeconds = randomTtlSeconds;
        this.nullTtlSeconds = nullTtlSeconds;
    }

    /** 缓存命中直接返回；MISS 由 Singleflight 合并回源。视频不存在返回 null（空值缓存挡住后续请求） */
    public VideoVO getDetail(long videoId) {
        String key = KEY_PREFIX + videoId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                return null;
            }
            return readJson(cached, key);
        }
        return singleflight.call(key, () -> {
            Video video = videoMapper.selectById(videoId);
            if (video == null) {
                // 空值缓存：短 TTL，防同一不存在 id 反复穿透
                redis.opsForValue().set(key, NULL_MARKER, Duration.ofSeconds(nullTtlSeconds));
                return null;
            }
            User author = userMapper.selectById(video.getUserId());
            VideoVO vo = VideoVO.from(video, author);
            // 基础 TTL + 随机抖动，防大量 key 同时到期造成雪崩
            long ttl = videoTtlSeconds + ThreadLocalRandom.current().nextLong(randomTtlSeconds);
            redis.opsForValue().set(key, writeJson(vo), Duration.ofSeconds(ttl));
            log.debug("缓存回填: {} TTL={}s", key, ttl);
            return vo;
        });
    }

    /** 数据变更后失效缓存（必须在外层事务提交之后调用） */
    public void evict(long videoId) {
        redis.delete(KEY_PREFIX + videoId);
    }

    private VideoVO readJson(String json, String key) {
        try {
            return objectMapper.readValue(json, VideoVO.class);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，删除坏 key={} : {}", key, e.getMessage());
            redis.delete(key);
            return null;
        }
    }

    private String writeJson(VideoVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (Exception e) {
            throw new IllegalStateException("缓存序列化失败", e);
        }
    }
}
