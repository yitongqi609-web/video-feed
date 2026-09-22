package com.videofeed.controller;

import com.videofeed.auth.UserContext;
import com.videofeed.common.ApiResponse;
import com.videofeed.common.PageResult;
import com.videofeed.dto.CommentCreateRequest;
import com.videofeed.dto.CommentVO;
import com.videofeed.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos/{videoId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ApiResponse<CommentVO> add(@PathVariable long videoId, @Valid @RequestBody CommentCreateRequest req) {
        return ApiResponse.ok(commentService.add(videoId, UserContext.uid(), req.content()));
    }

    @GetMapping
    public ApiResponse<PageResult<CommentVO>> list(@PathVariable long videoId,
                                                   @RequestParam(defaultValue = "0") long cursor,
                                                   @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(commentService.list(videoId, cursor, Math.min(limit, 50)));
    }
}
