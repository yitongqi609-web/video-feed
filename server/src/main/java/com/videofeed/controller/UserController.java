package com.videofeed.controller;

import com.videofeed.auth.UserContext;
import com.videofeed.common.ApiResponse;
import com.videofeed.dto.UserProfileVO;
import com.videofeed.dto.UserVO;
import com.videofeed.mapper.UserMapper;
import com.videofeed.service.FollowService;
import com.videofeed.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final FollowService followService;
    private final VideoService videoService;

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.ok(videoService.authorOf(UserContext.uid()));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserProfileVO> profile(@PathVariable long id) {
        return ApiResponse.ok(followService.profile(UserContext.uid(), id));
    }
}
