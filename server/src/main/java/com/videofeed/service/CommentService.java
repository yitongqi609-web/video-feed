package com.videofeed.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.videofeed.cache.VideoCacheService;
import com.videofeed.common.BizException;
import com.videofeed.common.PageResult;
import com.videofeed.common.TxUtils;
import com.videofeed.dto.CommentVO;
import com.videofeed.entity.Comment;
import com.videofeed.entity.User;
import com.videofeed.entity.Video;
import com.videofeed.id.SnowflakeIdGenerator;
import com.videofeed.mapper.CommentMapper;
import com.videofeed.mapper.UserMapper;
import com.videofeed.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final SnowflakeIdGenerator idGen;
    private final OutboxService outboxService;
    private final VideoCacheService videoCacheService;

    @Transactional
    public CommentVO add(long videoId, long userId, String content) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw BizException.notFound("视频不存在");
        }
        Comment comment = new Comment();
        comment.setId(idGen.nextId());
        comment.setVideoId(videoId);
        comment.setUserId(userId);
        comment.setContent(content);
        commentMapper.insert(comment);

        videoMapper.update(null, Wrappers.<Video>lambdaUpdate()
                .eq(Video::getId, videoId)
                .setSql("comment_count = comment_count + 1"));
        outboxService.append("VIDEO_COMMENT", videoId, Map.of("videoId", videoId, "type", "COMMENT"));
        TxUtils.afterCommit(() -> videoCacheService.evict(videoId));

        User author = userMapper.selectById(userId);
        return toVO(comment, author);
    }

    /** 评论列表：与 Feed 同一套游标分页（雪花ID倒序，多查一条判 hasMore） */
    public PageResult<CommentVO> list(long videoId, long cursor, int limit) {
        List<Comment> comments = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getVideoId, videoId)
                .lt(cursor > 0, Comment::getId, cursor)
                .orderByDesc(Comment::getId)
                .last("LIMIT " + (limit + 1)));
        PageResult<Comment> page = PageResult.of(comments, limit, Comment::getId);
        if (page.list().isEmpty()) {
            return new PageResult<>(List.of(), 0, page.hasMore());
        }
        Map<Long, User> authors = userMapper.selectBatchIds(
                        page.list().stream().map(Comment::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<CommentVO> vos = page.list().stream()
                .map(c -> toVO(c, authors.get(c.getUserId())))
                .toList();
        return new PageResult<>(vos, page.nextCursor(), page.hasMore());
    }

    private CommentVO toVO(Comment c, User author) {
        return new CommentVO(c.getId(), c.getVideoId(), c.getContent(), c.getCreatedAt(),
                author == null ? null : new com.videofeed.dto.UserVO(
                        author.getId(), author.getUsername(), author.getNickname(), author.getAvatarUrl()));
    }
}
