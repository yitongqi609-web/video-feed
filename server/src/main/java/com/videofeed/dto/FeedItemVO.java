package com.videofeed.dto;

/**
 * Feed 流条目：视频 + 当前用户是否已点赞
 */
public record FeedItemVO(VideoVO video, boolean liked) {
}
