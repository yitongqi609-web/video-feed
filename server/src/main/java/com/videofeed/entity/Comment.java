package com.videofeed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {

    /** 雪花ID，(video_id, id) 联合索引支撑评论游标分页 */
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long videoId;

    private Long userId;

    private String content;

    private LocalDateTime createdAt;
}
