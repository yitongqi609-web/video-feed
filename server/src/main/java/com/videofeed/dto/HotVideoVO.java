package com.videofeed.dto;

/**
 * 热门榜条目：名次 + 热度分（Redis ZSet score）
 */
public record HotVideoVO(int rank, VideoVO video, long score, boolean liked) {
}
