package com.videofeed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * (user_id, video_id) 唯一索引在数据库层兜底防重复点赞
 */
@Data
@TableName("user_like")
public class UserLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long videoId;

    private LocalDateTime createdAt;
}
