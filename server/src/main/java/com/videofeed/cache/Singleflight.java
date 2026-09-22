package com.videofeed.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Singleflight：合并同 key 的并发回源请求（Go singleflight 的 Java 手写版）。
 *
 * 缓存失效瞬间 N 个请求同时 MISS，只有一个请求真正查库回填缓存，
 * 其余请求挂在这个 in-flight Future 上等结果——防止缓存击穿打穿数据库。
 */
@Component
public class Singleflight {

    private final ConcurrentHashMap<String, CompletableFuture<Object>> inflight = new ConcurrentHashMap<>();
    private final Executor executor;

    public Singleflight(@Qualifier("sideTaskExecutor") Executor executor) {
        this.executor = executor;
    }

    @SuppressWarnings("unchecked")
    public <T> T call(String key, Supplier<T> loader) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = inflight.putIfAbsent(key, mine);
        if (running != null) {
            // 已有同 key 回源在进行中，直接等它的结果
            return (T) join(running);
        }
        executor.execute(() -> {
            try {
                mine.complete(loader.get());
            } catch (Throwable t) {
                mine.completeExceptionally(t);
            } finally {
                inflight.remove(key, mine);
            }
        });
        return (T) join(mine);
    }

    private Object join(CompletableFuture<Object> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new CompletionException(cause);
        }
    }
}
