package com.videofeed.dto;

/**
 * 视频详情：在 FeedItem 基础上补充是否关注了作者
 */
public record VideoDetailVO(VideoVO video, boolean liked, boolean followedAuthor) {
}
