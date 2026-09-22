package com.videofeed.auth;

import com.videofeed.common.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256）。
 * Access / Refresh 用同一个密钥，靠 type 声明区分。
 */
@Component
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtService(@Value("${app.auth.secret}") String secret,
                      @Value("${app.auth.access-ttl-seconds}") long accessTtlSeconds,
                      @Value("${app.auth.refresh-ttl-seconds}") long refreshTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public record TokenInfo(String token, String jti, long expiresAtMillis) {
        public long ttlSeconds() {
            return Math.max(1, (expiresAtMillis - System.currentTimeMillis()) / 1000);
        }
    }

    public TokenInfo generateAccessToken(long uid, String username) {
        return generate(uid, username, TYPE_ACCESS, accessTtlSeconds);
    }

    public TokenInfo generateRefreshToken(long uid, String username) {
        return generate(uid, username, TYPE_REFRESH, refreshTtlSeconds);
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    private TokenInfo generate(long uid, String username, String type, long ttlSeconds) {
        Instant now = Instant.now();
        Date expiresAt = Date.from(now.plusSeconds(ttlSeconds));
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .subject(username)
                .id(jti)
                .claim("uid", uid)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(expiresAt)
                .signWith(key)
                .compact();
        return new TokenInfo(token, jti, expiresAt.getTime());
    }

    /**
     * 校验签名与过期时间；不合法统一抛 401，具体原因只区分“过期/无效”两种，避免向攻击者泄露信息
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw BizException.unauthorized("令牌已过期");
        } catch (JwtException | IllegalArgumentException e) {
            throw BizException.unauthorized("令牌无效");
        }
    }

    public static long uidOf(Claims claims) {
        Object uid = claims.get("uid");
        if (uid instanceof Number n) {
            return n.longValue();
        }
        throw BizException.unauthorized("令牌缺少用户信息");
    }

    /** Refresh Token 在 Redis 中只存哈希，数据库/Redis 泄露也无法伪造令牌 */
    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
