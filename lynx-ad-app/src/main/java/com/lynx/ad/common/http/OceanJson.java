package com.lynx.ad.common.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class OceanJson {

    private final ObjectMapper objectMapper;

    public OceanJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode dataOrThrow(String body, int failCode, String failMessage) {
        JsonNode root = root(body);
        int code = root.path("code").asInt(-1);
        if (code == 40105) {
            throw new BizException(code, "访问令牌无效");
        }
        if (Qianchuan401ReplayCodes.isAuthExpiredCode(code)) {
            throw new BizException(code, "授权已失效");
        }
        if (code != 0) {
            String msg = root.path("message").asText(failMessage);
            throw new BizException(failCode, msg == null || msg.isBlank() ? failMessage : msg);
        }
        return root.path("data");
    }

    public JsonNode root(String body) {
        if (body == null || body.isBlank()) {
            throw new BizException(9999, "千川响应为空");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new BizException(9999, "千川响应解析失败");
        }
    }

    public static int peekCode(String body) {
        if (body == null || body.isBlank() || !body.contains("\"code\"")) {
            return -1;
        }
        try {
            int idx = body.indexOf("\"code\"");
            int colon = body.indexOf(':', idx);
            if (colon < 0) {
                return -1;
            }
            StringBuilder digits = new StringBuilder();
            for (int i = colon + 1; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    continue;
                }
                if (c == '-' || Character.isDigit(c)) {
                    digits.append(c);
                    continue;
                }
                break;
            }
            return digits.isEmpty() ? -1 : Integer.parseInt(digits.toString());
        } catch (Exception ex) {
            return -1;
        }
    }
}
