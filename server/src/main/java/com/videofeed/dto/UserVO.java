package com.videofeed.dto;

/**
 * 对外暴露的用户信息（不含密码哈希）
 */
public record UserVO(long id, String username, String nickname, String avatarUrl) {
}
