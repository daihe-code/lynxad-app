package com.lynx.ad.aiclient.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.aiclient.ChatCompletionClient;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "ai", havingValue = "false")
public class LiveChatCompletionClient implements ChatCompletionClient {

    private static final Logger log = LoggerFactory.getLogger(LiveChatCompletionClient.class);

    private final LynxAdProperties properties;
    private final QianchuanRestClient restClient;
    private final ObjectMapper objectMapper;

    public LiveChatCompletionClient(
            LynxAdProperties properties, QianchuanRestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String promptName, Map<String, Object> vars) {
        if (!properties.isAiLiveConfigured() || isBlank(properties.getAi().getLlm().getApiKey())) {
            throw new BizException(8001, "AI 未配置");
        }
        long start = System.currentTimeMillis();
        try {
            String prompt = loadPrompt(promptName) + "\n" + (vars == null ? "" : vars);
            String url = properties.getAi().getLlm().getBaseUrl();
            if (url == null || url.isBlank()) {
                throw new BizException(8001, "AI 未配置");
            }
            String body = restClient.exchange(
                    RequestSpec.post(url)
                            .header("Authorization", "Bearer " + properties.getAi().getLlm().getApiKey())
                            .json("{\"model\":\""
                                    + properties.getAi().getLlm().getModel()
                                    + "\",\"messages\":[{\"role\":\"user\",\"content\":"
                                    + objectMapper.writeValueAsString(prompt)
                                    + "}]}")
                            .build()
                            .withoutReplay(),
                    String.class);
            JsonNode root = objectMapper.readTree(body);
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                text = root.path("content").asText(root.path("text").asText(""));
            }
            if (text.isBlank()) {
                throw new BizException(8002, "AI 上游失败");
            }
            log.info(
                    "[AI-CALL] model={} inputToken={} outputToken={} costMs={}",
                    properties.getAi().getLlm().getModel(),
                    prompt.length() / 4,
                    text.length() / 4,
                    System.currentTimeMillis() - start);
            return text;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(8002, "AI 上游失败");
        }
    }

    private static String loadPrompt(String promptName) {
        String file = "prompt/creative/" + (promptName == null ? "copy" : promptName) + ".md";
        try (InputStream in = LiveChatCompletionClient.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                return promptName == null ? "" : promptName;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return promptName == null ? "" : promptName;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
