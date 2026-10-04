package com.lynx.ad.aiclient.mock;

import com.lynx.ad.aiclient.VideoGenClient;
import com.lynx.ad.aiclient.VideoJobStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "ai", havingValue = "true", matchIfMissing = true)
public class MockVideoGenClient implements VideoGenClient {

    @Override
    public String submit(String payload) {
        return "mock-video-job";
    }

    @Override
    public VideoJobStatus poll(String thirdPartyJobId) {
        return new VideoJobStatus(thirdPartyJobId, "SUCCESS", "");
    }
}
