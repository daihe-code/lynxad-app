package com.lynx.ad.modules.creative.infrastructure.adapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class VideoRemakeSchedulingConfiguration {
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler videoRemakeScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("video-remake-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
