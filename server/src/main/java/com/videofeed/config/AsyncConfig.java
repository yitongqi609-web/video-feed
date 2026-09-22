package com.videofeed.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 浏览量异步计数等旁路任务用的小线程池，不占用 Tomcat 工作线程
 */
@Configuration
public class AsyncConfig {

    @Bean("sideTaskExecutor")
    public Executor sideTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("side-task-");
        executor.initialize();
        return executor;
    }
}
