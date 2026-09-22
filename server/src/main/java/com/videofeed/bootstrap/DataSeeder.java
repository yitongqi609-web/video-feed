package com.videofeed.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.videofeed.entity.Comment;
import com.videofeed.entity.Follow;
import com.videofeed.entity.User;
import com.videofeed.entity.UserLike;
import com.videofeed.entity.Video;
import com.videofeed.id.SnowflakeIdGenerator;
import com.videofeed.mapper.CommentMapper;
import com.videofeed.mapper.FollowMapper;
import com.videofeed.mapper.UserLikeMapper;
import com.videofeed.mapper.UserMapper;
import com.videofeed.mapper.VideoMapper;
import com.videofeed.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动灌入演示数据（user 表为空时）。
 * 演示账号：demo / 123456（另有 alice、bob，密码同）
 * 视频用本地样例文件（server/media/，由后端 /media/** 提供），
 * 不依赖外部视频源——googleapis 被墙、部分环境外网媒体也拉不动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final List<String> VIDEO_URLS = List.of(
            "/media/bigbuck10s.mp4",
            "/media/jellyfish10s.mp4",
            "/media/sintel10s.mp4",
            "/media/movie300.mp4");

    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final UserLikeMapper userLikeMapper;
    private final CommentMapper commentMapper;
    private final FollowMapper followMapper;
    private final SnowflakeIdGenerator idGen;
    private final HotRankService hotRankService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private record SeedVideo(String title, String desc) {
    }

    private static final List<SeedVideo> SEED_VIDEOS = List.of(
            new SeedVideo("大雄兔：一只兔子的复仇计划", "Blender 开源动画经典之作，三只小坏蛋惹错了兔子"),
            new SeedVideo("大象之梦：世界上第一部开源电影", "2006 年 Blender 基金会出品， surreal 风格短片"),
            new SeedVideo("为更大的火焰：广告创意短片", "Google Chromecast 系列广告之一"),
            new SeedVideo("为更大的逃脱：越狱式广告", "Chromecast 广告系列：更大的屏幕，更大的逃脱"),
            new SeedVideo("为更大的乐趣", "Chromecast 广告系列：乐趣也要更大"),
            new SeedVideo("为更大的兜风", "Chromecast 广告系列：兜风体验升级"),
            new SeedVideo("为更大的崩溃", "Chromecast 广告系列：游戏时刻"),
            new SeedVideo("桑德：龙与少女的旅程", "Blender 开源电影，少女桑德寻找小龙的奇幻冒险"),
            new SeedVideo("街道与泥地试驾", "斯巴鲁傲虎真实路况试驾视频"),
            new SeedVideo("钢铁之泪：科幻短片", "Blender 实拍+CG 混合科幻短片，阿姆斯特丹的未来"),
            new SeedVideo("大众 GTI 评测", "汽车媒体对大众 GTI 的详细评测"),
            new SeedVideo("布尔拉力赛出发！", "一队跑车的拉力赛启程纪实"));

    @Override
    public void run(String... args) {
        Long userCount = userMapper.selectCount(null);
        if (userCount != null && userCount > 0) {
            return;
        }
        log.info("检测到空库，开始灌入演示数据...");

        String hash = passwordEncoder.encode("123456");
        User demo = newUser("demo", "演示账号", hash);
        User alice = newUser("alice", "小艾", hash);
        User bob = newUser("bob", "老王", hash);
        List<User> users = List.of(demo, alice, bob);

        for (int i = 0; i < SEED_VIDEOS.size(); i++) {
            SeedVideo seed = SEED_VIDEOS.get(i);
            User author = users.get(i % 3); // 三个作者轮流发
            Video video = new Video();
            video.setId(idGen.nextId());
            video.setUserId(author.getId());
            video.setTitle(seed.title());
            video.setDescription(seed.desc());
            video.setVideoUrl(VIDEO_URLS.get(i % VIDEO_URLS.size()));
            video.setCoverUrl("https://picsum.photos/seed/vf" + i + "/640/360");
            // 造一些初始热度：点赞/评论/浏览展示值（真实点赞行只造少量，防重复点赞逻辑不受影响）
            video.setLikeCount(8 + (i * 37 + 13) % 200);
            video.setCommentCount(2);
            video.setViewCount(100 + (i * 53 + 29) % 900);
            video.setCreatedAt(LocalDateTime.now().minusMinutes(90 - i * 6L));
            videoMapper.insert(video);

            // alice / bob 按规则真实点赞部分视频（当前登录 demo 能在 Feed 看到红心状态）
            if (i % 3 != 1) {
                like(alice.getId(), video.getId());
            }
            if (i % 2 == 0) {
                like(bob.getId(), video.getId());
            }
            // 每个视频两条真实评论
            addComment(video.getId(), alice.getId(), "第 " + (i + 1) + " 条：这个必须三连！");
            addComment(video.getId(), bob.getId(), "画质可以，已关注期待更新");
        }

        follow(demo.getId(), alice.getId());
        follow(demo.getId(), bob.getId());
        follow(alice.getId(), demo.getId());
        follow(bob.getId(), demo.getId());
        follow(alice.getId(), bob.getId());

        // 灌完数据立刻重建一次热榜，不用等定时任务
        hotRankService.rebuild();
        log.info("演示数据灌入完成：3 用户 / {} 视频（演示账号 demo/123456）", SEED_VIDEOS.size());
    }

    private User newUser(String username, String nickname, String hash) {
        User user = new User();
        user.setId(idGen.nextId());
        user.setUsername(username);
        user.setPasswordHash(hash);
        user.setNickname(nickname);
        user.setAvatarUrl("https://api.dicebear.com/7.x/notionists/svg?seed=" + username);
        userMapper.insert(user);
        return user;
    }

    private void like(long userId, long videoId) {
        UserLike userLike = new UserLike();
        userLike.setUserId(userId);
        userLike.setVideoId(videoId);
        try {
            userLikeMapper.insert(userLike);
        } catch (Exception ignored) {
            // 种子数据可能重复，忽略
        }
    }

    private void addComment(long videoId, long userId, String content) {
        Comment comment = new Comment();
        comment.setId(idGen.nextId());
        comment.setVideoId(videoId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        commentMapper.insert(comment);
    }

    private void follow(long followerId, long followeeId) {
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        try {
            followMapper.insert(follow);
        } catch (Exception ignored) {
        }
    }
}
