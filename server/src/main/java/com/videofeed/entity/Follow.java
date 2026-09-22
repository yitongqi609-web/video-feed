package com.videofeed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("follow")
public class Follow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者 */
    private Long followerId;

    /** 被关注者 */
    private Long followeeId;

    private LocalDateTime createdAt;
}
