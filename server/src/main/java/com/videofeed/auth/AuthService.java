package com.videofeed.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.videofeed.common.BizException;
import com.videofeed.dto.TokenPair;
import com.videofeed.dto.UserVO;
import com.videofeed.entity.User;
import com.videofeed.id.SnowflakeIdGenerator;
import com.videofeed.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 登录态管理：Access/Refresh 双令牌。
 *
 * 核心设计：
 * 1. Refresh Token 只存 SHA-256 哈希到 Redis（key = auth:refresh:{uid}）
 * 2. 刷新时用 Lua 脚本原子完成「校验旧哈希 + 写入新哈希」：
 *    并发携带同一个 Refresh Token 刷新时，只有一个请求 CAS 成功，其余返回失败——天然防并发轮换错乱
 * 3. 登出时 Access Token 的 jti 进黑名单（剩余有效期作为 TTL），Refresh 哈希直接删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY = "auth:refresh:";
    private static final String BLACKLIST_KEY = "auth:blk:";

    /**
     * KEYS[1] = auth:refresh:{uid}
     * ARGV[1] = 旧令牌哈希  ARGV[2] = 新令牌哈希  ARGV[3] = 新 TTL（秒）
     * 返回 1=轮换成功 0=哈希不存在（已登出/过期） -1=哈希不匹配（旧令牌重放）
     */
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local cur = redis.call('GET', KEYS[1])
            if not cur then return 0 end
            if cur ~= ARGV[1] then return -1 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[3]))
            return 1
            """, Long.class);

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final SnowflakeIdGenerator idGen;
    private final StringRedisTemplate redis;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public TokenPair register(String username, String password, String nickname) {
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw BizException.conflict("用户名已被注册");
        }
        User user = new User();
        user.setId(idGen.nextId());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setAvatarUrl("https://api.dicebear.com/7.x/notionists/svg?seed=" + username);
        userMapper.insert(user);
        log.info("新用户注册: {}({})", username, user.getId());
        return issueTokenPair(user);
    }

    public TokenPair login(String username, String password) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        // 用户不存在与密码错误返回同一提示，避免撞库探测
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        return issueTokenPair(user);
    }

    public TokenPair refresh(String refreshToken) {
        Claims claims = jwtService.parse(refreshToken); // 过期/签名错误 -> 401
        if (!JwtService.TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw BizException.unauthorized("请使用 Refresh Token 调用本接口");
        }
        long uid = JwtService.uidOf(claims);
        String username = claims.getSubject();
        String refreshKey = REFRESH_KEY + uid;

        // 先生成新令牌再 CAS：CAS 成功的那一刻新哈希已生效
        JwtService.TokenInfo newRefresh = jwtService.generateRefreshToken(uid, username);
        Long result = redis.execute(ROTATE_SCRIPT,
                List.of(refreshKey),
                JwtService.sha256(refreshToken),
                JwtService.sha256(newRefresh.token()),
                String.valueOf(newRefresh.ttlSeconds()));

        if (result == null || result == 0) {
            throw BizException.unauthorized("登录已过期，请重新登录");
        }
        if (result == -1) {
            // 旧令牌再次出现：要么并发刷新落败，要么令牌被窃取重放。一律吊销整个会话，强制重新登录
            redis.delete(refreshKey);
            throw BizException.unauthorized("Refresh Token 已失效（已被轮换），请重新登录");
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        JwtService.TokenInfo newAccess = jwtService.generateAccessToken(uid, username);
        return new TokenPair(newAccess.token(), newRefresh.token(), newAccess.ttlSeconds(), toVO(user));
    }

    /** 登出：Access 的 jti 进黑名单（剩余有效期为 TTL），Refresh 哈希删除 */
    public void logout(String accessToken, long uid) {
        Claims claims = jwtService.parse(accessToken);
        String jti = claims.getId();
        long remainMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainMillis > 0) {
            redis.opsForValue().set(BLACKLIST_KEY + jti, "1", Duration.ofMillis(remainMillis));
        }
        redis.delete(REFRESH_KEY + uid);
        log.info("用户登出: uid={}, jti={}", uid, jti);
    }

    private TokenPair issueTokenPair(User user) {
        JwtService.TokenInfo access = jwtService.generateAccessToken(user.getId(), user.getUsername());
        JwtService.TokenInfo refresh = jwtService.generateRefreshToken(user.getId(), user.getUsername());
        redis.opsForValue().set(REFRESH_KEY + user.getId(),
                JwtService.sha256(refresh.token()), Duration.ofSeconds(refresh.ttlSeconds()));
        return new TokenPair(access.token(), refresh.token(), access.ttlSeconds(), toVO(user));
    }

    private UserVO toVO(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl());
    }
}
