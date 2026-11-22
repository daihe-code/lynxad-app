package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抖音链接视频下载器。
 *
 * <p>主路由为免登录 H5 路由：解析分享短链后，两次请求 iesdouyin 分享页（第一次取回 ttwid
 * 设备 Cookie，第二次携带该 Cookie），从页面内嵌的 videoInfoRes JSON 中取出播放地址，把
 * playwm 换成 play 得到无水印播放地址，再下载并用 ffprobe 校验。
 *
 * <p>备用路由为自托管的 Evil0ctal Douyin_TikTok_Download_API 实例：仅在配置了
 * {@code lynx.ad.remake-link.download-api-base-url} 与
 * {@code lynx.ad.remake-link.download-api-path} 时启用；主路由失败后才尝试，两个路由都失败时
 * 返回主路由的错误，不静默替换为其他视频。备用接口的具体路径随自托管实例的版本而不同
 * （旧版为同步接口，新版默认返回异步任务），请按实例的 {@code /docs} 填写；若备用接口只返回
 * 异步任务而不直接返回视频地址，本方法会明确报错，不会等待或猜测任务结果。
 *
 * <p>本类只做解析与下载，不登录抖音、不保存任何账号 Cookie；下载到的文件只写入临时文件，
 * 调用方负责在入库后删除。
 */
public final class DouyinLinkVideoDownloader {
    public static final long MAX_BYTES = 200L * 1024 * 1024;
    public static final String PLATFORM_DOUYIN = "douyin";
    public static final String METHOD_H5 = "douyin-h5";
    public static final String METHOD_DOWNLOAD_API = "douyin-download-api";

    private static final Set<String> SUPPORTED_HOSTS = Set.of(
            "v.douyin.com", "www.douyin.com", "douyin.com", "www.iesdouyin.com", "iesdouyin.com");
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>，。；]+");
    private static final Pattern AWEME_PATTERN = Pattern.compile("/(?:share/)?video/(\\d+)");
    private static final Pattern URL_LIST_PATTERN =
            Pattern.compile("\"url_list\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
    private static final Pattern PLAY_URL_PATTERN =
            Pattern.compile("https?://[^\"'\\s\\\\]+play[^\"'\\s\\\\]*");

    public record VideoProbe(double durationSeconds, int width, int height) {}

    public record DownloadedVideo(Path file, long size, String sha256Hex, String sourceUrl,
                                  String platform, String downloadMethod, String filename,
                                  VideoProbe probe) {}

    private DouyinLinkVideoDownloader() {}

    public static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
    }

    /** 从粘贴的分享文本中取出第一个链接，并校验它属于抖音。 */
    public static String normalizeShareUrl(String raw) {
        String url = extractUrl(raw);
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw input("请粘贴抖音视频链接。");
        }
        if (uri.getUserInfo() != null
                || (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))) {
            throw input("请粘贴抖音视频链接。");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_HOSTS.contains(host)) {
            throw input("仅支持抖音视频链接（v.douyin.com 短链或 douyin.com/video/…）。");
        }
        return url;
    }

    public static String extractUrl(String raw) {
        if (raw == null || raw.isBlank()) throw input("请粘贴抖音视频链接。");
        Matcher matcher = URL_PATTERN.matcher(raw.strip());
        if (!matcher.find()) throw input("请粘贴抖音视频链接。");
        String found = matcher.group();
        while (!found.isEmpty() && ")]}，".indexOf(found.charAt(found.length() - 1)) >= 0) {
            found = found.substring(0, found.length() - 1);
        }
        return found;
    }

    /** 从视频页链接中取出视频编号；短链解析前可能取不到，返回 null。 */
    public static String awemeId(String normalizedUrl) {
        if (normalizedUrl == null) return null;
        Matcher matcher = AWEME_PATTERN.matcher(normalizedUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 抖音已发布的无水印播放地址与有水印地址只差 playwm/play 一段。 */
    public static String withoutWatermark(String playUrl) {
        return playUrl == null ? null : playUrl.replaceFirst("playwm", "play");
    }

    /**
     * 从分享页 HTML 中取出播放地址。先找 videoInfoRes 对象，再退到 url_list，
     * 最后退到页面中出现的播放地址；三种方式都找不到时返回 null。
     */
    public static String extractPlayUrl(String html, ObjectMapper json) {
        if (html == null || html.isBlank()) return null;
        String found = extractPlayUrlFromText(html.replace("\\/", "/"), json);
        if (found != null) return found;
        try {
            String decoded = java.net.URLDecoder.decode(html, StandardCharsets.UTF_8).replace("\\/", "/");
            if (!decoded.equals(html)) return extractPlayUrlFromText(decoded, json);
        } catch (IllegalArgumentException ignored) {
            // 不是 URL 编码的正文，按原文处理即可。
        }
        return null;
    }

    private static String extractPlayUrlFromText(String text, ObjectMapper json) {
        int searchFrom = 0;
        while (true) {
            int key = text.indexOf("videoInfoRes", searchFrom);
            if (key < 0) break;
            int brace = text.indexOf('{', key);
            if (brace < 0) break;
            String object = balancedObject(text, brace);
            if (object != null) {
                try {
                    String found = findPlayUrl(json.readTree(object));
                    if (found != null) return withoutWatermark(found);
                } catch (Exception ignored) {
                    // 该段不是完整 JSON，继续找下一处。
                }
            }
            searchFrom = brace + 1;
        }
        Matcher list = URL_LIST_PATTERN.matcher(text);
        if (list.find()) return withoutWatermark(list.group(1));
        Matcher any = PLAY_URL_PATTERN.matcher(text);
        return any.find() ? withoutWatermark(any.group()) : null;
    }

    /** 在 JSON 树中递归查找 play_addr.url_list 的第一条地址。 */
    public static String findPlayUrl(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        if (node.isObject()) {
            JsonNode playAddr = node.get("play_addr");
            if (playAddr != null) {
                JsonNode urls = playAddr.path("url_list");
                if (urls.isArray() && !urls.isEmpty() && urls.get(0).isTextual()) {
                    return urls.get(0).asText();
                }
                if (playAddr.isTextual()) return playAddr.asText();
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                String found = findPlayUrl(fields.next().getValue());
                if (found != null) return found;
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String found = findPlayUrl(item);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String balancedObject(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return null;
    }

    /**
     * 主入口：先走 H5 路由；H5 路由失败且配置了备用接口时再走备用路由；
     * 两个路由都失败时返回主路由的错误，不用其他视频替代。
     */
    public static DownloadedVideo download(ObjectMapper json, String rawInput,
            String downloadApiBaseUrl, String downloadApiPath, String downloadApiKey, String ffprobePath) {
        String normalized = normalizeShareUrl(rawInput);
        HttpClient http = httpClient();
        BizException h5Failure = null;
        try {
            return downloadViaH5(http, json, normalized, ffprobePath);
        } catch (BizException ex) {
            h5Failure = ex;
        }
        String base = downloadApiBaseUrl == null ? "" : downloadApiBaseUrl.strip();
        String path = downloadApiPath == null ? "" : downloadApiPath.strip();
        if (base.isBlank() || path.isBlank()) throw h5Failure;
        try {
            return downloadViaDownloadApi(http, json, normalized, base, path, downloadApiKey, ffprobePath);
        } catch (BizException ex) {
            throw input(h5Failure.getMessage() + "备用下载接口也未能获取该视频，请稍后再试或上传本地视频。");
        }
    }

    private static DownloadedVideo downloadViaH5(HttpClient http, ObjectMapper json,
            String normalizedUrl, String ffprobePath) {
        URI shareUri = URI.create(normalizedUrl);
        var first = fetchSharePage(http, shareUri);
        // 第二次请求：Cookie 管理器会自动带上第一次取回的 ttwid 设备 Cookie。
        var second = fetchSharePage(http, shareUri);
        String host = second.uri().getHost();
        if (host != null && host.contains("passport")) {
            throw input("该视频需要登录后才能查看，请确认链接指向的是公开视频。");
        }
        String html = second.body().isBlank() ? first.body() : second.body();
        if (html.contains("need_login")) {
            throw input("该视频需要登录后才能查看，请确认链接指向的是公开视频。");
        }
        String playUrl = extractPlayUrl(html, json);
        if (playUrl == null) {
            throw input("未能从该链接解析出视频地址，该视频可能为私密、已删除或需要登录，请确认链接指向的是公开视频。");
        }
        Path file = downloadToTempFile(http, withoutWatermark(playUrl));
        return finish(file, normalizedUrl, PLATFORM_DOUYIN, METHOD_H5, json, ffprobePath);
    }

    private static HttpResponse<String> fetchSharePage(HttpClient http, URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", "https://www.douyin.com/")
                .GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 404) {
                throw input("该视频可能已被删除或链接无效，请确认链接后重试。");
            }
            if (status == 403 || status == 429) {
                throw input("抖音暂时拒绝了本次访问，请稍后再试或上传本地视频。");
            }
            if (status < 200 || status >= 300) {
                throw input("链接暂时无法访问（抖音返回 " + status + "），请稍后再试或上传本地视频。");
            }
            return response;
        } catch (BizException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw input("链接访问被中断，请稍后重试。");
        } catch (IOException ex) {
            throw input("链接暂时无法访问，请检查链接后重试。");
        }
    }

    private static DownloadedVideo downloadViaDownloadApi(HttpClient http, ObjectMapper json,
            String normalizedUrl, String baseUrl, String apiPath, String apiKey, String ffprobePath) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        URI uri = URI.create(base + path + "?url=" + URLEncoder.encode(normalizedUrl, StandardCharsets.UTF_8));
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").header("User-Agent", "lynxad-app");
        if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey.strip());
        HttpResponse<String> response;
        try {
            response = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw input("备用下载接口访问被中断，请稍后重试。");
        } catch (IOException ex) {
            throw input("备用下载接口暂时无法访问，未能获取该视频。");
        }
        if (response.statusCode() != 200) {
            throw input("备用下载接口返回 " + response.statusCode() + "，未能获取该视频。");
        }
        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (Exception ex) {
            throw input("备用下载接口返回内容无法解析，未能获取该视频。");
        }
        String playUrl = findVideoUrl(root);
        if (playUrl == null && root.hasNonNull("task_id")) {
            throw input("备用下载接口为异步接口，未直接返回视频地址，请在自托管实例中确认任务状态后再试。");
        }
        if (playUrl == null) throw input("备用下载接口未返回视频地址，未能获取该视频。");
        Path file = downloadToTempFile(http, withoutWatermark(playUrl));
        return finish(file, normalizedUrl, PLATFORM_DOUYIN, METHOD_DOWNLOAD_API, json, ffprobePath);
    }

    /** 在备用接口的返回 JSON 中递归查找视频地址（video_url / download_url / play_url 等）。 */
    public static String findVideoUrl(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String name = field.getKey().toLowerCase(Locale.ROOT);
                JsonNode value = field.getValue();
                if (value.isTextual() && value.asText().startsWith("http")
                        && (name.contains("video") || name.contains("download") || name.contains("play")
                        || name.equals("url") || name.equals("href"))) {
                    return value.asText();
                }
                String found = findVideoUrl(value);
                if (found != null) return found;
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String found = findVideoUrl(item);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Path downloadToTempFile(HttpClient http, String playUrl) {
        Path temp;
        try {
            temp = Files.createTempFile("remake-link-", ".mp4");
        } catch (IOException ex) {
            throw input("服务器暂时无法保存链接视频，请稍后重试。");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(playUrl)).timeout(Duration.ofSeconds(120))
                .header("User-Agent", USER_AGENT).header("Referer", "https://www.iesdouyin.com/")
                .GET().build();
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            deleteQuietly(temp);
            throw input("链接视频下载被中断，请稍后重试。");
        } catch (IOException | IllegalArgumentException ex) {
            deleteQuietly(temp);
            throw input("链接视频暂时无法下载，请稍后再试或上传本地视频。");
        }
        if (response.statusCode() != 200) {
            deleteQuietly(temp);
            throw input("链接视频暂时无法下载（视频地址返回 " + response.statusCode() + "），请稍后再试或上传本地视频。");
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.isBlank() && contentType.contains("text/html")) {
            deleteQuietly(temp);
            throw input("下载到的不是视频文件，该视频可能为私密、已删除或需要登录。");
        }
        long total = 0;
        try (InputStream in = response.body(); var out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw input("链接视频超过 200 MB，请上传本地视频。");
                }
                out.write(buffer, 0, read);
            }
        } catch (BizException ex) {
            deleteQuietly(temp);
            throw ex;
        } catch (IOException ex) {
            deleteQuietly(temp);
            throw input("链接视频下载失败，请稍后再试或上传本地视频。");
        }
        return temp;
    }

    private static DownloadedVideo finish(Path file, String sourceUrl, String platform, String method,
            ObjectMapper json, String ffprobePath) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            deleteQuietly(file);
            throw input("下载到的视频文件无法读取，请稍后重试。");
        }
        if (size <= 0) {
            deleteQuietly(file);
            throw input("下载到的视频文件为空，请确认链接指向的是有效视频。");
        }
        if (size > MAX_BYTES) {
            deleteQuietly(file);
            throw input("链接视频超过 200 MB，请上传本地视频。");
        }
        String sha256 = sha256Hex(file);
        VideoProbe probe = probe(file, ffprobePath, json);
        String aweme = awemeId(sourceUrl);
        String filename = "douyin-" + (aweme == null ? "video" : aweme) + ".mp4";
        return new DownloadedVideo(file, size, sha256, sourceUrl, platform, method, filename, probe);
    }

    public static String sha256Hex(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw input("无法校验下载到的视频文件，请稍后重试。");
        }
    }

    /**
     * 用 ffprobe 读取真实视频流并校验规格；与前端本地上传的校验口径一致
     * （4–120 秒、横屏 16:9 或竖屏 9:16、短边 720–1440 像素、长边不超过 2560 像素）。
     * ffprobe 不可用或校验失败时明确报错，不放行未校验的视频。
     */
    public static VideoProbe probe(Path file, String ffprobePath, ObjectMapper json) {
        String command = ffprobePath == null || ffprobePath.isBlank() ? "ffprobe" : ffprobePath.strip();
        if (file == null || !Files.isRegularFile(file)) throw input("链接视频文件缺失，请重新粘贴链接。");
        Process process;
        try {
            process = new ProcessBuilder(command, "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height,duration",
                    "-show_entries", "format=duration", "-of", "json", file.toString())
                    .redirectErrorStream(true).start();
        } catch (IOException ex) {
            throw input("服务器未安装 ffprobe，暂时无法校验链接视频，请稍后重试或上传本地视频。");
        }
        boolean finished;
        try {
            finished = process.waitFor(60, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw input("视频校验超时，请稍后重试。");
        }
        if (!finished) {
            process.destroyForcibly();
            throw input("视频校验超时，请稍后重试。");
        }
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw input("无法读取视频校验结果，请稍后重试。");
        }
        if (process.exitValue() != 0) throw input("无法校验该视频，请确认链接指向的是有效的视频文件。");
        JsonNode root;
        try {
            root = json.readTree(output);
        } catch (Exception ex) {
            throw input("无法校验该视频，请确认链接指向的是有效的视频文件。");
        }
        JsonNode stream = root.path("streams").path(0);
        int width = stream.path("width").asInt(0);
        int height = stream.path("height").asInt(0);
        double duration = stream.path("duration").asDouble(Double.NaN);
        if (Double.isNaN(duration)) duration = root.path("format").path("duration").asDouble(Double.NaN);
        if (width <= 0 || height <= 0 || Double.isNaN(duration) || !Double.isFinite(duration)) {
            throw input("无法读取该视频的时长与尺寸，请确认链接指向的是有效的视频文件。");
        }
        return validateSpec(duration, width, height);
    }

    public static VideoProbe validateSpec(double durationSeconds, int width, int height) {
        if (durationSeconds < 3.98 || durationSeconds > 120.02) throw input("参考视频须为 4–120 秒。");
        int shortEdge = Math.min(width, height);
        int longEdge = Math.max(width, height);
        if (shortEdge < 720 || shortEdge > 1440 || longEdge > 2560) {
            throw input("参考视频短边须为 720–1440 像素，长边不超过 2560 像素。");
        }
        if (Math.abs((double) longEdge / shortEdge - 16.0 / 9.0) > 0.005) {
            throw input("参考视频须为横屏 16:9 或竖屏 9:16。");
        }
        return new VideoProbe(durationSeconds, width, height);
    }

    private static void deleteQuietly(Path file) {
        try {
            if (file != null) Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 临时文件删除失败不影响本次请求的错误返回。
        }
    }

    private static BizException input(String message) {
        return new BizException(1402, message);
    }
}
