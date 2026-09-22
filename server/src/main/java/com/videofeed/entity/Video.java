package com.videofeed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video")
public class Video {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String videoUrl;

    private String coverUrl;

    private Integer likeCount;

    private Integer commentCount;

    private Integer viewCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
