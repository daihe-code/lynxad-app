package com.lynx.ad.modules.creative.application;

import com.lynx.ad.modules.creative.infrastructure.persistence.CarouselRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CarouselReviewWorker {
    private final CarouselRepository repository;
    private final CarouselService service;
    public CarouselReviewWorker(CarouselRepository repository,CarouselService service) { this.repository=repository; this.service=service; }
    @Scheduled(fixedDelay=3000)
    public void poll() {
        for(var bundle:repository.due()) {
            try { service.refresh(bundle); }
            catch(Exception ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("图文质检汇总暂时失败，未放行组件同步，图文 ID={}",bundle.id()); }
        }
    }
}
