package com.videofeed.common;

import java.util.List;

/**
 * 统一游标分页结果：多查一条判断 hasMore，nextCursor 传给下一页请求
 */
public record PageResult<T>(List<T> list, long nextCursor, boolean hasMore) {

    /** limit+1 查询后截断 */
    public static <T> PageResult<T> of(List<T> list, int limit, java.util.function.ToLongFunction<T> cursorGetter) {
        boolean hasMore = list.size() > limit;
        List<T> page = hasMore ? list.subList(0, limit) : list;
        long nextCursor = page.isEmpty() ? 0 : cursorGetter.applyAsLong(page.get(page.size() - 1));
        return new PageResult<>(page, nextCursor, hasMore);
    }
}
