package com.videofeed.dto;

/**
 * 用户主页信息：附带粉丝/关注数与当前用户是否已关注
 */
public record UserProfileVO(long id, String username, String nickname, String avatarUrl,
                            long followerCount, long followeeCount, boolean hasFollowed) {
}
