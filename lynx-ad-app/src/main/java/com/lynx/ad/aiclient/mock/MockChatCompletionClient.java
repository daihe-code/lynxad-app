package com.lynx.ad.aiclient.mock;

import com.lynx.ad.aiclient.ChatCompletionClient;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "ai", havingValue = "true", matchIfMissing = true)
public class MockChatCompletionClient implements ChatCompletionClient {

    @Override
    public String complete(String promptName, Map<String, Object> vars) {
        String name = promptName == null ? "" : promptName.toLowerCase();
        if (name.contains("title")) {
            return "烘焙好物推荐";
        }
        if (name.contains("copy") || name.contains("script")) {
            return "今日必吃黄油可颂，外酥里软。";
        }
        if (name.contains("story") || name.contains("board")) {
            return "[{\"shot\":1,\"text\":\"开场展示面包\"}]";
        }
        return "稳定短文本";
    }
}
