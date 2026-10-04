package com.lynx.ad.aiclient;

import java.util.Map;

public interface ChatCompletionClient {
    String complete(String promptName, Map<String, Object> vars);
}
