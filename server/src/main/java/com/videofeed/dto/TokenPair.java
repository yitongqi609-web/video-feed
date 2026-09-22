package com.videofeed.dto;

/**
 * 登录态：Access 短令牌 + Refresh 长令牌
 */
public record TokenPair(String accessToken, String refreshToken, long accessExpiresIn, UserVO user) {
}
