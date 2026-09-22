package com.videofeed.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.videofeed.common.BizException;
import com.videofeed.dto.UserProfileVO;
import com.videofeed.entity.Follow;
import com.videofeed.entity.User;
import com.videofeed.mapper.FollowMapper;
import com.videofeed.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    @Transactional
    public void follow(long followerId, long followeeId) {
        if (followerId == followeeId) {
            throw BizException.badRequest("不能关注自己");
        }
        User followee = userMapper.selectById(followeeId);
        if (followee == null) {
            throw BizException.notFound("用户不存在");
        }
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        try {
            followMapper.insert(follow);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("已关注该用户");
        }
    }

    @Transactional
    public void unfollow(long followerId, long followeeId) {
        int deleted = followMapper.delete(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        if (deleted == 0) {
            throw BizException.badRequest("尚未关注该用户");
        }
    }

    public boolean hasFollowed(long followerId, long followeeId) {
        if (followerId == followeeId) {
            return false;
        }
        Long count = followMapper.selectCount(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        return count != null && count > 0;
    }

    public UserProfileVO profile(long viewerId, long targetId) {
        User user = userMapper.selectById(targetId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        Long followerCount = followMapper.selectCount(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFolloweeId, targetId));
        Long followeeCount = followMapper.selectCount(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowerId, targetId));
        return new UserProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl(),
                followerCount == null ? 0 : followerCount,
                followeeCount == null ? 0 : followeeCount,
                hasFollowed(viewerId, targetId));
    }
}
