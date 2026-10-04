package com.lynx.ad.aiclient.mock;

import com.lynx.ad.aiclient.TtsClient;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreativeFixture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "ai", havingValue = "true", matchIfMissing = true)
public class MockTtsClient implements TtsClient {

    @Override
    public byte[] synth(String text) {
        return CreativeFixture.mp4().length > 8 ? new byte[] {0, 0, 0, 0} : new byte[] {0, 0, 0, 0};
    }
}
