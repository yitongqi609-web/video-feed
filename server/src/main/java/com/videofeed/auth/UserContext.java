package com.videofeed.auth;

/**
 * 每个请求的当前登录用户，由 JwtInterceptor 写入、afterCompletion 清理
 */
public final class UserContext {

    private static final ThreadLocal<Long> UID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long uid, String username) {
        UID.set(uid);
        USERNAME.set(username);
    }

    public static long uid() {
        Long uid = UID.get();
        if (uid == null) {
            throw new IllegalStateException("当前线程没有登录上下文");
        }
        return uid;
    }

    public static String username() {
        return USERNAME.get();
    }

    public static void clear() {
        UID.remove();
        USERNAME.remove();
    }
}
