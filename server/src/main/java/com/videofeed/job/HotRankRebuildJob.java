package com.videofeed.job;

import com.videofeed.service.HotRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热榜周期全量重建：从 DB 聚合计数重算热度覆盖 ZSet。
 * 兜底意义：增量丢失（消费失败/Redis 清空）都能被校准回来，保证最终一致。
 */
@Component
@RequiredArgsConstructor
public class HotRankRebuildJob {

    private final HotRankService hotRankService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void rebuild() {
        hotRankService.rebuild();
    }
}
