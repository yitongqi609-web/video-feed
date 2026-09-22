package com.videofeed.controller;

import com.videofeed.auth.UserContext;
import com.videofeed.common.ApiResponse;
import com.videofeed.common.PageResult;
import com.videofeed.dto.FeedItemVO;
import com.videofeed.dto.HotVideoVO;
import com.videofeed.dto.VideoCreateRequest;
import com.videofeed.dto.VideoDetailVO;
import com.videofeed.dto.VideoVO;
import com.videofeed.service.LikeService;
import com.videofeed.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final LikeService likeService;

    @PostMapping("/videos")
    public ApiResponse<VideoVO> publish(@Valid @RequestBody VideoCreateRequest req) {
        return ApiResponse.ok(videoService.publish(UserContext.uid(), req));
    }

    @GetMapping("/videos/{id}")
    public ApiResponse<VideoDetailVO> detail(@PathVariable long id) {
        return ApiResponse.ok(videoService.detail(id, UserContext.uid()));
    }

    @PostMapping("/videos/{id}/like")
    public ApiResponse<Void> like(@PathVariable long id) {
        likeService.like(UserContext.uid(), id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/videos/{id}/like")
    public ApiResponse<Void> unlike(@PathVariable long id) {
        likeService.unlike(UserContext.uid(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/feed")
    public ApiResponse<PageResult<FeedItemVO>> publicFeed(@RequestParam(defaultValue = "0") long cursor,
                                                          @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(videoService.publicFeed(UserContext.uid(), cursor, Math.min(limit, 50)));
    }

    @GetMapping("/feed/following")
    public ApiResponse<PageResult<FeedItemVO>> followingFeed(@RequestParam(defaultValue = "0") long cursor,
                                                             @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(videoService.followingFeed(UserContext.uid(), cursor, Math.min(limit, 50)));
    }

    @GetMapping("/feed/hot")
    public ApiResponse<List<HotVideoVO>> hotFeed(@RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(videoService.hotFeed(UserContext.uid(), Math.min(size, 50)));
    }
}
