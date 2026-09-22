package com.videofeed.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.videofeed.cache.VideoCacheService;
import com.videofeed.common.PageResult;
import com.videofeed.dto.FeedItemVO;
import com.videofeed.dto.HotVideoVO;
import com.videofeed.dto.UserVO;
import com.videofeed.dto.VideoCreateRequest;
import com.videofeed.dto.VideoDetailVO;
import com.videofeed.dto.VideoVO;
import com.videofeed.entity.User;
import com.videofeed.entity.Video;
import com.videofeed.id.SnowflakeIdGenerator;
import com.videofeed.mapper.UserMapper;
import com.videofeed.mapper.VideoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VideoService {

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final SnowflakeIdGenerator idGen;
    private final LikeService likeService;
    private final FollowService followService;
    private final VideoCacheService videoCacheService;
    private final HotRankService hotRankService;
    private final Executor executor;
    private final int viewWeight;

    public VideoService(VideoMapper videoMapper, UserMapper userMapper, SnowflakeIdGenerator idGen,
                        LikeService likeService, FollowService followService,
                        VideoCacheService videoCacheService, HotRankService hotRankService,
                        @Qualifier("sideTaskExecutor") Executor executor,
                        @Value("${app.hot.view-weight}") int viewWeight) {
        this.videoMapper = videoMapper;
        this.userMapper = userMapper;
        this.idGen = idGen;
        this.likeService = likeService;
        this.followService = followService;
        this.videoCacheService = videoCacheService;
        this.hotRankService = hotRankService;
        this.executor = executor;
        this.viewWeight = viewWeight;
    }

    public VideoVO publish(long userId, VideoCreateRequest req) {
        User author = userMapper.selectById(userId);
        Video video = new Video();
        video.setId(idGen.nextId());
        video.setUserId(userId);
        video.setTitle(req.title());
        video.setDescription(req.description());
        video.setVideoUrl(req.videoUrl());
        video.setCoverUrl(req.coverUrl());
        video.setLikeCount(0);
        video.setCommentCount(0);
        video.setViewCount(0);
        videoMapper.insert(video);
        log.info("用户{}发布视频{}", userId, video.getId());
        return VideoVO.from(video, author);
    }

    /**
     * 视频详情：走 Cache-Aside 缓存。
     * 浏览量旁路异步处理：DB 自增 + 热度 ZINCRBY，不阻塞响应；
     * 高频浏览不走 Outbox（允许少量丢失，由周期全量重建兜底校准）。
     */
    public VideoDetailVO detail(long videoId, long viewerId) {
        VideoVO vo = videoCacheService.getDetail(videoId);
        if (vo == null) {
            throw com.videofeed.common.BizException.notFound("视频不存在");
        }
        boolean liked = likeService.hasLiked(viewerId, videoId);
        boolean followed = followService.hasFollowed(viewerId, vo.userId());
        CompletableFuture.runAsync(() -> {
            try {
                videoMapper.update(null, Wrappers.<Video>lambdaUpdate()
                        .eq(Video::getId, videoId)
                        .setSql("view_count = view_count + 1"));
                hotRankService.increase(videoId, viewWeight);
            } catch (Exception e) {
                log.warn("浏览计数旁路处理失败 videoId={}: {}", videoId, e.getMessage());
            }
        }, executor);
        return new VideoDetailVO(vo, liked, followed);
    }

    /** 公共 Feed：按雪花ID倒序游标分页（多查一条判 hasMore，无深分页 OFFSET） */
    public PageResult<FeedItemVO> publicFeed(long viewerId, long cursor, int limit) {
        List<Video> videos = videoMapper.selectList(Wrappers.<Video>lambdaQuery()
                .lt(cursor > 0, Video::getId, cursor)
                .orderByDesc(Video::getId)
                .last("LIMIT " + (limit + 1)));
        return enrich(PageResult.of(videos, limit, Video::getId), viewerId);
    }

    /** 关注 Feed：只看关注作者的视频，同一套游标分页 */
    public PageResult<FeedItemVO> followingFeed(long viewerId, long cursor, int limit) {
        List<Video> videos = videoMapper.selectList(Wrappers.<Video>lambdaQuery()
                .inSql(Video::getUserId, "SELECT followee_id FROM follow WHERE follower_id = " + viewerId)
                .lt(cursor > 0, Video::getId, cursor)
                .orderByDesc(Video::getId)
                .last("LIMIT " + (limit + 1)));
        return enrich(PageResult.of(videos, limit, Video::getId), viewerId);
    }

    /** 热门 Feed：ZSet 降序 TopN，视频详情逐个走缓存 */
    public List<HotVideoVO> hotFeed(long viewerId, int size) {
        List<HotRankService.Scored> top = hotRankService.topWithScores(size);
        List<HotVideoVO> result = new ArrayList<>(top.size());
        Set<Long> likedIds = likeService.likedVideoIds(viewerId,
                top.stream().map(HotRankService.Scored::videoId).toList());
        int rank = 1;
        for (HotRankService.Scored scored : top) {
            VideoVO vo = videoCacheService.getDetail(scored.videoId());
            if (vo == null) {
                continue; // 视频已删，跳过（空值缓存也挡住了对 DB 的穿透）
            }
            result.add(new HotVideoVO(rank++, vo, (long) scored.score(), likedIds.contains(scored.videoId())));
        }
        return result;
    }

    /** 一页视频批量补作者信息 + 当前用户点赞状态 */
    private PageResult<FeedItemVO> enrich(PageResult<Video> page, long viewerId) {
        if (page.list().isEmpty()) {
            return new PageResult<>(List.of(), 0, page.hasMore());
        }
        Map<Long, User> authors = userMapper.selectBatchIds(
                        page.list().stream().map(Video::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> likedIds = likeService.likedVideoIds(viewerId,
                page.list().stream().map(Video::getId).toList());
        List<FeedItemVO> items = page.list().stream()
                .map(v -> new FeedItemVO(VideoVO.from(v, authors.get(v.getUserId())), likedIds.contains(v.getId())))
                .toList();
        return new PageResult<>(items, page.nextCursor(), page.hasMore());
    }

    public UserVO authorOf(long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? null : new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl());
    }
}
