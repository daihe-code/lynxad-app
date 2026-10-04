package com.lynx.ad.aiclient.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.aiclient.VideoGenClient;
import com.lynx.ad.aiclient.VideoJobStatus;
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
public class LiveVideoGenClient implements VideoGenClient {

    private static final Logger log = LoggerFactory.getLogger(LiveVideoGenClient.class);

    private final LynxAdProperties properties;
    private final QianchuanRestClient restClient;
    private final ObjectMapper objectMapper;

    public LiveVideoGenClient(
            LynxAdProperties properties, QianchuanRestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String submit(String payload) {
        assertConfigured();
        try {
            String endpoint = properties.getAi().getKeling().getEndpoint();
            String body = restClient.exchange(
                    RequestSpec.post(endpoint + (endpoint.endsWith("/") ? "" : "/") + "submit")
                            .json(payload == null ? "{}" : payload)
                            .build()
                            .withoutReplay(),
                    String.class);
            JsonNode root = objectMapper.readTree(body);
            String jobId = root.path("job_id").asText(root.path("task_id").asText(root.path("id").asText("")));
            if (jobId.isBlank()) {
                throw new BizException(8002, "AI 上游失败");
            }
            return jobId;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(8002, "AI 上游失败");
        }
    }

    @Override
    public VideoJobStatus poll(String thirdPartyJobId) {
        assertConfigured();
        long start = System.currentTimeMillis();
        int max = Math.max(1, properties.getAi().getKeling().getMaxPoll());
        long wait = Math.max(1, properties.getAi().getKeling().getPollMs());
        String endpoint = properties.getAi().getKeling().getEndpoint();
        try {
            for (int i = 0; i < max; i++) {
                String body = restClient.exchange(
                        RequestSpec.get(endpoint + (endpoint.endsWith("/") ? "" : "/") + "jobs/" + thirdPartyJobId)
                                .build()
                                .withoutReplay(),
                        String.class);
                JsonNode root = objectMapper.readTree(body);
                String status = root.path("status").asText("RUNNING").toUpperCase();
                String url = root.path("url").asText("");
                if ("SUCCESS".equals(status) || "SUCCEEDED".equals(status) || "DONE".equals(status)) {
                    log.info("[AI-CALL] model=keling inputToken=0 outputToken=0 costMs={}", System.currentTimeMillis() - start);
                    return new VideoJobStatus(thirdPartyJobId, "SUCCESS", url);
                }
                if ("FAIL".equals(status) || "FAILED".equals(status)) {
                    throw new BizException(8002, "AI 上游失败");
                }
                Thread.sleep(wait);
            }
            throw new BizException(8002, "AI 上游失败");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BizException(8002, "AI 上游失败");
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(8002, "AI 上游失败");
        }
    }

    private void assertConfigured() {
        if (!properties.isAiLiveConfigured()) {
            throw new BizException(8001, "AI 未配置");
        }
    }
}
