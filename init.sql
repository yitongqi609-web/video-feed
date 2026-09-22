-- 短视频 Feed 后端 - 数据库初始化（容器首次启动执行）
-- 种子演示数据由后端 DataSeeder 启动时写入（密码需 BCrypt 加密，SQL 里写不了）

CREATE DATABASE IF NOT EXISTS video_feed DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE video_feed;

-- 用户
CREATE TABLE `user` (
  id            BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  nickname      VARCHAR(50)  NOT NULL,
  avatar_url    VARCHAR(500) NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT ='用户';

-- 视频
CREATE TABLE video (
  id            BIGINT        NOT NULL PRIMARY KEY COMMENT '雪花ID',
  user_id       BIGINT        NOT NULL,
  title         VARCHAR(200)  NOT NULL,
  description   VARCHAR(2000) NULL,
  video_url     VARCHAR(500)  NOT NULL,
  cover_url     VARCHAR(500)  NULL,
  like_count    INT           NOT NULL DEFAULT 0,
  comment_count INT           NOT NULL DEFAULT 0,
  view_count    INT           NOT NULL DEFAULT 0,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_id (user_id, id),
  KEY idx_created (created_at)
) ENGINE = InnoDB COMMENT ='视频';

-- 点赞：(user_id, video_id) 唯一索引兜底防重复点赞
CREATE TABLE user_like (
  id         BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT   NOT NULL,
  video_id   BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_video (user_id, video_id),
  KEY idx_video (video_id, user_id)
) ENGINE = InnoDB COMMENT ='点赞';

-- 评论：雪花ID，(video_id, id) 联合索引支撑游标分页
CREATE TABLE comment (
  id         BIGINT        NOT NULL PRIMARY KEY COMMENT '雪花ID',
  video_id   BIGINT        NOT NULL,
  user_id    BIGINT        NOT NULL,
  content    VARCHAR(1000) NOT NULL,
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_video (video_id, id)
) ENGINE = InnoDB COMMENT ='评论';

-- 关注
CREATE TABLE follow (
  id          BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
  follower_id BIGINT   NOT NULL COMMENT '关注者',
  followee_id BIGINT   NOT NULL COMMENT '被关注者',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_follower_followee (follower_id, followee_id),
  KEY idx_followee (followee_id)
) ENGINE = InnoDB COMMENT ='关注';

-- Outbox 事件表：与业务同事务写入，Relay 轮询投递 MQ
CREATE TABLE outbox_event (
  id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_type    VARCHAR(50)   NOT NULL COMMENT 'VIDEO_LIKE / VIDEO_UNLIKE / VIDEO_COMMENT',
  aggregate_id  BIGINT        NOT NULL COMMENT '聚合根ID（视频ID）',
  payload       VARCHAR(2000) NOT NULL COMMENT 'JSON',
  status        VARCHAR(20)   NOT NULL DEFAULT 'NEW' COMMENT 'NEW/SENT/FAILED',
  retry_count   INT           NOT NULL DEFAULT 0,
  next_retry_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status_retry (status, next_retry_at)
) ENGINE = InnoDB COMMENT ='Outbox事件';
