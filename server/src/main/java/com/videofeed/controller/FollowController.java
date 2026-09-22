package com.videofeed.controller;

import com.videofeed.auth.UserContext;
import com.videofeed.common.ApiResponse;
import com.videofeed.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping
    public ApiResponse<Void> follow(@PathVariable long userId) {
        followService.follow(UserContext.uid(), userId);
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> unfollow(@PathVariable long userId) {
        followService.unfollow(UserContext.uid(), userId);
        return ApiResponse.ok();
    }
}
