package com.videofeed.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.videofeed.entity.User;
import com.videofeed.entity.Video;

import java.time.LocalDateTime;

/**
 * 视频视图对象（缓存中存的就是它的 JSON）
 */
public record VideoVO(long id, long userId, String title, String description, String videoUrl,
                      String coverUrl, int likeCount, int commentCount, int viewCount,
                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
                      UserVO author) {

    public static VideoVO from(Video v, User author) {
        UserVO authorVO = author == null ? null
                : new UserVO(author.getId(), author.getUsername(), author.getNickname(), author.getAvatarUrl());
        return new VideoVO(v.getId(), v.getUserId(), v.getTitle(), v.getDescription(),
                v.getVideoUrl(), v.getCoverUrl(),
                v.getLikeCount() == null ? 0 : v.getLikeCount(),
                v.getCommentCount() == null ? 0 : v.getCommentCount(),
                v.getViewCount() == null ? 0 : v.getViewCount(),
                v.getCreatedAt(), authorVO);
    }

    public VideoVO withAuthor(UserVO author) {
        return new VideoVO(id, userId, title, description, videoUrl, coverUrl,
                likeCount, commentCount, viewCount, createdAt, author);
    }
}
