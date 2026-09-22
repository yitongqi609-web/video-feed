package com.videofeed.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.videofeed.cache.VideoCacheService;
import com.videofeed.common.BizException;
import com.videofeed.common.TxUtils;
import com.videofeed.entity.UserLike;
import com.videofeed.entity.Video;
import com.videofeed.mapper.UserLikeMapper;
import com.videofeed.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 点赞一致性：同一个本地事务完成「点赞关系 + 计数 + Outbox 事件」三件事
 *  1. INSERT user_like，(user_id, video_id) 唯一索引兜底，并发重复点赞只成功一个
 *  2. UPDATE video SET like_count = like_count + 1 原子自增，不读不改
 *  3. Outbox 落事件，事务提交后由 Relay 投 MQ 更新热榜
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final UserLikeMapper userLikeMapper;
    private final VideoMapper videoMapper;
    private final OutboxService outboxService;
    private final VideoCacheService videoCacheService;

    @Transactional
    public void like(long userId, long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw BizException.notFound("视频不存在");
        }
        UserLike userLike = new UserLike();
        userLike.setUserId(userId);
        userLike.setVideoId(videoId);
        try {
            userLikeMapper.insert(userLike);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("已点赞过该视频");
        }
        videoMapper.update(null, Wrappers.<Video>lambdaUpdate()
                .eq(Video::getId, videoId)
                .setSql("like_count = like_count + 1"));
        outboxService.append("VIDEO_LIKE", videoId, Map.of("videoId", videoId, "type", "LIKE"));
        // 缓存删除必须等事务提交后执行
        TxUtils.afterCommit(() -> videoCacheService.evict(videoId));
    }

    @Transactional
    public void unlike(long userId, long videoId) {
        int deleted = userLikeMapper.delete(Wrappers.<UserLike>lambdaQuery()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getVideoId, videoId));
        if (deleted == 0) {
            throw BizException.badRequest("尚未点赞该视频");
        }
        videoMapper.update(null, Wrappers.<Video>lambdaUpdate()
                .eq(Video::getId, videoId)
                .setSql("like_count = like_count - 1"));
        outboxService.append("VIDEO_UNLIKE", videoId, Map.of("videoId", videoId, "type", "UNLIKE"));
        TxUtils.afterCommit(() -> videoCacheService.evict(videoId));
    }

    public boolean hasLiked(long userId, long videoId) {
        Long count = userLikeMapper.selectCount(Wrappers.<UserLike>lambdaQuery()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getVideoId, videoId));
        return count != null && count > 0;
    }

    /** 批量判断当前用户点赞状态（Feed 一页视频只需一次 IN 查询） */
    public Set<Long> likedVideoIds(long userId, Collection<Long> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Set.of();
        }
        List<UserLike> likes = userLikeMapper.selectList(Wrappers.<UserLike>lambdaQuery()
                .eq(UserLike::getUserId, userId)
                .in(UserLike::getVideoId, videoIds));
        return likes.stream().map(UserLike::getVideoId).collect(Collectors.toSet());
    }
}
