package com.lynx.ad.common.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.infrastructure.adapter.Qianchuan401ReplayInterceptor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QianchuanRestClient {

    private static final Logger log = LoggerFactory.getLogger(QianchuanRestClient.class);
    private static final long[] BACKOFF_MS = {2_000L, 4_000L, 8_000L, 16_000L};
    private static final int MAX_RETRIES = 3;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;
    private final ObjectProvider<Qianchuan401ReplayInterceptor> replayInterceptor;

    @Autowired
    public QianchuanRestClient(ObjectMapper objectMapper, ObjectProvider<Qianchuan401ReplayInterceptor> replayInterceptor) {
        this(objectMapper, Sleeper.THREAD, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), replayInterceptor);
    }

    public QianchuanRestClient(ObjectMapper objectMapper, Sleeper sleeper, HttpClient httpClient) {
        this(objectMapper, sleeper, httpClient, null);
    }

    public QianchuanRestClient(
            ObjectMapper objectMapper,
            Sleeper sleeper,
            HttpClient httpClient,
            ObjectProvider<Qianchuan401ReplayInterceptor> replayInterceptor) {
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
        this.httpClient = httpClient;
        this.replayInterceptor = replayInterceptor;
    }

    public <T> T exchange(RequestSpec spec, Class<T> type) {
        try {
            return exchangeOnce(spec, type);
        } catch (BizException ex) {
            if (!shouldReplay(spec, ex)) {
                throw ex;
            }
            replayInterceptor.getObject().refreshOnce(new AccountId(spec.replayAccountId()));
            try {
                return exchangeOnce(spec, type);
            } catch (BizException second) {
                if (Qianchuan401ReplayCodes.isAuthExpired(second)) {
                    throw new BizException(1211, "授权已失效，请重新授权");
                }
                throw second;
            }
        }
    }

    public HttpResponse<String> exchangeRaw(RequestSpec spec) {
        int attempt = 0;
        while (true) {
            HttpResponse<String> response = doExchange(spec);
            int status = response.statusCode();
            // A POST may have committed before a 5xx response; never blindly recreate a task.
            if (shouldRetry(status) && ("GET".equalsIgnoreCase(spec.method()) || status == 429) && attempt < MAX_RETRIES) {
                log.warn("qianchuan retry status={} attempt={}", status, attempt + 1);
                sleepQuietly(BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)]);
                attempt++;
                continue;
            }
            return response;
        }
    }

    private <T> T exchangeOnce(RequestSpec spec, Class<T> type) {
        HttpResponse<String> response = exchangeRaw(spec);
        int status = response.statusCode();
        int ocean = OceanJson.peekCode(response.body());
        if (Qianchuan401ReplayCodes.isAuthExpiredCode(status) || Qianchuan401ReplayCodes.isAuthExpiredCode(ocean)) {
            throw new BizException(status == 401 ? 401 : ocean, "授权已失效");
        }
        if (status >= 400) {
            throw new BizException(status, "千川调用失败");
        }
        return readBody(response.body(), type);
    }

    private boolean shouldReplay(RequestSpec spec, BizException ex) {
        return spec.replayAccountId() != null
                && !spec.replayAccountId().isBlank()
                && replayInterceptor != null
                && replayInterceptor.getIfAvailable() != null
                && Qianchuan401ReplayCodes.isAuthExpired(ex);
    }

    private HttpResponse<String> doExchange(RequestSpec spec) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(spec.url()))
                    .timeout(Duration.ofSeconds(20));
            spec.headers().forEach(builder::header);
            String method = spec.method().toUpperCase(Locale.ROOT);
            if ("GET".equals(method) || "DELETE".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(spec.body() == null ? "" : spec.body()));
            }
            log.info("qianchuan {} {}", method, sanitizeUrl(spec.url()));
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BizException(9999, "千川调用中断");
        } catch (Exception ex) {
            throw new BizException(9999, "千川网络异常");
        }
    }

    private <T> T readBody(String body, Class<T> type) {
        if (type == String.class) {
            @SuppressWarnings("unchecked")
            T cast = (T) (body == null ? "" : body);
            return cast;
        }
        if (body == null || body.isBlank() || type == Void.class || type == Void.TYPE) {
            return null;
        }
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception ex) {
            throw new BizException(9999, "千川响应解析失败");
        }
    }

    private void sleepQuietly(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(9999, "千川重试中断");
        }
    }

    private static boolean shouldRetry(int status) {
        return status == 429 || status >= 500;
    }

    private static String sanitizeUrl(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    /** Streams multipart bytes; a transport failure is ambiguous and must not be blindly retried. */
    public String multipart(String url, String token, java.util.Map<String, String> fields,
            String fileField, java.nio.file.Path file, String filename, String contentType) {
        String boundary = "lynx-" + java.util.UUID.randomUUID();
        java.util.List<HttpRequest.BodyPublisher> pieces = new java.util.ArrayList<>();
        fields.forEach((name, value) -> pieces.add(HttpRequest.BodyPublishers.ofString(
                "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")));
        String safeName = filename.replaceAll("[\r\n\"\\\\]", "_");
        pieces.add(HttpRequest.BodyPublishers.ofString("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                + fileField + "\"; filename=\"" + safeName + "\"\r\nContent-Type: " + contentType + "\r\n\r\n"));
        try {
            pieces.add(HttpRequest.BodyPublishers.ofFile(file));
            pieces.add(HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10))
                    .header("Access-Token", token).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.concat(pieces.toArray(HttpRequest.BodyPublisher[]::new))).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = OceanJson.peekCode(response.body());
            if (Qianchuan401ReplayCodes.isAuthExpiredCode(response.statusCode()) || Qianchuan401ReplayCodes.isAuthExpiredCode(code))
                throw new BizException(1211, "千川授权失效，请等待授权模块更新或重新授权");
            if (response.statusCode() == 429) throw new BizException(429, "千川上传限流，请稍后重试");
            if (response.statusCode() >= 500) throw new BizException(1523, "千川响应异常，上传结果待确认");
            if (response.statusCode() >= 400) throw new BizException(1511, "千川拒绝上传，请检查文件与权限");
            return response.body();
        } catch (BizException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new BizException(1523, "上传中断，千川结果待确认"); }
        catch (Exception ex) { throw new BizException(1523, "网络中断，千川上传结果待确认"); }
    }
}
