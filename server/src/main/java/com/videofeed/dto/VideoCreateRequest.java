package com.videofeed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发布视频：以 URL 形式引用视频/封面资源（同原项目，不做文件上传转码）
 */
public record VideoCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 500) String description,
        @NotBlank String videoUrl,
        String coverUrl) {
}
