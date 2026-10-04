package com.lynx.ad.starter;

import com.lynx.ad.common.config.LynxAdProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.lynx.ad")
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(LynxAdProperties.class)
@MapperScan("com.lynx.ad.modules.**.infrastructure.persistence")
public class LynxAdApplication {

    public static void main(String[] args) {
        SpringApplication.run(LynxAdApplication.class, args);
    }
}
