package com.videofeed.service;

import com.videofeed.entity.Video;
import com.videofeed.mapper.VideoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 热门榜：Redis ZSet（member=videoId, score=加权热度）
 *
 * 写入两条路径：
 *  1. 实时增量：互动事件经 Outbox→RocketMQ 后 ZINCRBY（最终一致）
 *  2. 周期全量重建：@Scheduled 从 DB 重算覆盖，校准漂移 + 恢复 Redis 数据丢失
 */
@Slf4j
@Service
public class HotRankService {

    public static final String HOT_KEY = "hot:rank";

    private final StringRedisTemplate redis;
    private final VideoMapper videoMapper;

    private final int likeWeight;
    private final int commentWeight;
    private final int viewWeight;
    private final int topN;

    public HotRankService(StringRedisTemplate redis, VideoMapper videoMapper,
                          @Value("${app.hot.like-weight}") int likeWeight,
                          @Value("${app.hot.comment-weight}") int commentWeight,
                          @Value("${app.hot.view-weight}") int viewWeight,
                          @Value("${app.hot.top-n}") int topN) {
        this.redis = redis;
        this.videoMapper = videoMapper;
        this.likeWeight = likeWeight;
        this.commentWeight = commentWeight;
        this.viewWeight = viewWeight;
        this.topN = topN;
    }

    /** 互动事件热度增量（消费者调用） */
    public void increase(long videoId, double delta) {
        redis.opsForZSet().incrementScore(HOT_KEY, String.valueOf(videoId), delta);
    }

    public record Scored(long videoId, double score) {
    }

    /** ZSet 降序取前 n：ZREVRANGE WITHSCORES */
    public List<Scored> topWithScores(int n) {
        List<Scored> result = new ArrayList<>();
        var tuples = redis.opsForZSet().reverseRangeWithScores(HOT_KEY, 0, n - 1);
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                result.add(new Scored(Long.parseLong(t.getValue()), t.getScore() == null ? 0 : t.getScore()));
            }
        }
        return result;
    }

    /**
     * 全量重建：按 DB 聚合计数重算所有视频热度，覆盖写入 ZSet 并裁剪只留 TopN。
     * 兜底手段：增量丢失、Redis 清空、权重调整后都能被校准回来。
     */
    public void rebuild() {
        List<Video> videos = videoMapper.selectList(null);
        var zset = redis.opsForZSet();
        int written = 0;
        for (Video v : videos) {
            long score = score(v);
            if (score > 0) {
                zset.add(HOT_KEY, String.valueOf(v.getId()), score);
                written++;
            }
        }
        if (written > topN) {
            // 保留 score 最高的 topN 个（ZSet 升序序号 0 ~ -(topN+1) 全删）
            zset.removeRange(HOT_KEY, 0, -(topN + 1));
        }
        log.info("热门榜全量重建完成: 视频总数={}, 写入={}, 热度公式=点赞*{}+评论*{}+浏览*{}",
                videos.size(), written, likeWeight, commentWeight, viewWeight);
    }

    public long score(Video v) {
        long like = v.getLikeCount() == null ? 0 : v.getLikeCount();
        long comment = v.getCommentCount() == null ? 0 : v.getCommentCount();
        long view = v.getViewCount() == null ? 0 : v.getViewCount();
        return like * likeWeight + comment * commentWeight + view * viewWeight;
    }
}
