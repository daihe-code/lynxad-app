package com.lynx.ad.common.http;

import java.util.LinkedHashMap;
import java.util.Map;

public record RequestSpec(String method, String url, Map<String, String> headers, String body, String replayAccountId) {

    public RequestSpec {
        if (method == null || method.isBlank()) {
            method = "GET";
        }
        if (headers == null) {
            headers = Map.of();
        }
    }

    public static Builder get(String url) {
        return new Builder("GET", url);
    }

    public static Builder post(String url) {
        return new Builder("POST", url);
    }

    public RequestSpec withoutReplay() {
        return new RequestSpec(method, url, headers, body, null);
    }

    public static class Builder {
        private final String method;
        private final String url;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String body;
        private String replayAccountId;

        public Builder(String method, String url) {
            this.method = method;
            this.url = url;
        }

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Builder json(String json) {
            this.body = json;
            headers.putIfAbsent("Content-Type", "application/json");
            return this;
        }

        public Builder replayAccountId(String accountId) {
            this.replayAccountId = accountId;
            return this;
        }

        public RequestSpec build() {
            return new RequestSpec(method, url, Map.copyOf(headers), body, replayAccountId);
        }
    }
}
