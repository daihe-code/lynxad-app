package com.lynx.ad.aiclient.live;

import com.lynx.ad.aiclient.TtsClient;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "ai", havingValue = "false")
public class LiveTtsClient implements TtsClient {

    private static final Logger log = LoggerFactory.getLogger(LiveTtsClient.class);

    private final LynxAdProperties properties;
    private final QianchuanRestClient restClient;

    public LiveTtsClient(LynxAdProperties properties, QianchuanRestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public byte[] synth(String text) {
        if (properties.getAi().getTts().getApiKey() == null || properties.getAi().getTts().getApiKey().isBlank()) {
            throw new BizException(8001, "AI 未配置");
        }
        long start = System.currentTimeMillis();
        try {
            String body = restClient.exchange(
                    RequestSpec.post(properties.getAi().getTts().getBaseUrl())
                            .header("Authorization", "Bearer " + properties.getAi().getTts().getApiKey())
                            .json("{\"text\":\"" + (text == null ? "" : text.replace("\"", "'")) + "\"}")
                            .build()
                            .withoutReplay(),
                    String.class);
            log.info("[AI-CALL] model=tts inputToken={} outputToken=0 costMs={}",
                    text == null ? 0 : text.length() / 4,
                    System.currentTimeMillis() - start);
            return body == null ? new byte[] {0, 0, 0, 0} : body.getBytes();
        } catch (BizException ex) {
            if (ex.code() == 8001) {
                throw ex;
            }
            throw new BizException(8002, "AI 上游失败");
        }
    }
}
