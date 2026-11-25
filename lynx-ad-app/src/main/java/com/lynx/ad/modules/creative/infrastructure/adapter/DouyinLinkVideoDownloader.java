package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抖音链接视频下载器。
 *
 * <p>主路由为免登录 H5 路由：解析分享短链后，两次请求 iesdouyin 分享页（第一次取回 ttwid
 * 设备 Cookie，第二次携带该 Cookie），从页面内嵌的 videoInfoRes JSON 中取出播放地址，把
 * playwm 换成 play 得到无水印播放地址；取不到时再直取 www.douyin.com 视频页（iesdouyin 现会 302 到该页），并对双重转义的内嵌 JSON 做反转义后提取；再下载并用 ffprobe 校验。
 *
 * <p>兜底顺序：H5 路由失败后，先试 aweme 详情接口
 * （{@code /aweme/v1/web/aweme/detail/}，只用链接或短链解析出的同一视频编号请求，
 * 返回的编号不一致时不使用），再试自托管备用接口。失败时返回最明确的失败原因，不用其他视频替代。
 *
 * <p>备用路由为自托管的 Evil0ctal Douyin_TikTok_Download_API 实例：仅在配置了
 * {@code lynx.ad.remake-link.download-api-base-url} 与
 * {@code lynx.ad.remake-link.download-api-path} 时启用；主路由失败后才尝试，两个路由都失败时
 * 返回主路由的错误，不静默替换为其他视频。备用接口的具体路径随自托管实例的版本而不同
 * （旧版为同步接口，新版默认返回异步任务），请按实例的 {@code /docs} 填写；若备用接口只返回
 * 异步任务而不直接返回视频地址，本方法会轮询任务结果（见 DownloadApiTaskPoller），最多等待约 20 秒。
 *
 * <p>搜索页链接（/search/…）没有单个视频地址：若带 modal_id，会先尝试打开该视频；否则直接
 * 提示用户先打开视频再复制分享链接。
 *
 * <p>失败时抛出 {@link LinkImportFailure}，携带失败原因码与中文提示；原因码随错误响应返回，
 * 细节只写服务端日志，不向用户暴露堆栈。
 *
 * <p>本类只做解析与下载，不登录抖音、不保存任何账号 Cookie；下载到的文件只写入临时文件，
 * 调用方负责在入库后删除。
 */
public final class DouyinLinkVideoDownloader {
    public static final long MAX_BYTES = 200L * 1024 * 1024;
    public static final String PLATFORM_DOUYIN = "douyin";
    public static final String METHOD_H5 = "douyin-h5";
    public static final String METHOD_DETAIL_API = "douyin-detail-api";
    public static final String METHOD_DOWNLOAD_API = "douyin-download-api";

    private static final Set<String> SUPPORTED_HOSTS = Set.of(
            "v.douyin.com", "www.douyin.com", "douyin.com", "www.iesdouyin.com", "iesdouyin.com");
    /**
     * 抖音视频播放地址所在的媒体域名（含字节跳动自有 CDN）。解析出的播放地址指向这里的域名时，
     * 它就是抖音视频本身，不是外站视频。抖音返回了列表之外的新 CDN 主机时，被拒绝的错误会点名该
     * 主机，便于在核对归属后补充到本列表；不要放行无法确认归属的域名。
     */
    private static final Set<String> MEDIA_DOMAINS = Set.of("douyin.com", "iesdouyin.com", "douyinvod.com",
            "zjcdn.com", "snssdk.com", "pstatp.com", "bytecdn.cn");
    private static final Set<Integer> REDIRECT_CODES = Set.of(301, 302, 303, 307, 308);
    private static final int MAX_SHARE_PAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DETAIL_JSON_BYTES = 4 * 1024 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1";
    private static final Pattern DOUYIN_LINK_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9.-])(?:https?://)?(?:[A-Za-z0-9-]+\\.)*(?:v\\.douyin\\.com|douyin\\.com|iesdouyin\\.com)(/[^\\s\"'<>，。；、！？]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AWEME_PATTERN = Pattern.compile("/(?:share/)?video/(\\d+)");
    private static final Pattern URL_LIST_PATTERN =
            Pattern.compile("\"url_list\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
    private static final Pattern PLAY_URL_PATTERN =
            Pattern.compile("https?://[^\"'\\s\\\\]+play[^\"'\\s\\\\]*");
    /** H5 路由失败后值得用详情接口再试一次的原因；规格失败等表示已拿到同一视频，不再重下。 */
    private static final Set<String> DETAIL_RETRYABLE = Set.of(
            LinkImportFailure.SHARE_PAGE_FETCH, LinkImportFailure.TTWID_MISSING,
            LinkImportFailure.VIDEOINFO_MISSING, LinkImportFailure.PLAY_URL_EMPTY,
            LinkImportFailure.DOWNLOAD_FAILED);

    /** 链接下载整条链路最多尝试的次数（含首次）：首次失败后最多再试两次。 */
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    /** 两次尝试之间的等待时间。 */
    private static final long RETRY_DELAY_MILLIS = 2000L;

    /**
     * 值得把整条链路再试一次的失败原因（瞬时失败）。确定性失败（视频私密／已删除／需要登录／
     * 规格失败／视频过大）与链接格式／搜索页失败不在此列，出现时直接抛出，不重试。
     */
    // 包内可见，供单测断言可重试原因集合。
    static final Set<String> RETRYABLE_FAILURES = Set.of(
            LinkImportFailure.SHARE_PAGE_FETCH, LinkImportFailure.SHORT_LINK_RESOLVE,
            LinkImportFailure.TTWID_MISSING, LinkImportFailure.VIDEOINFO_MISSING,
            LinkImportFailure.PLAY_URL_EMPTY, LinkImportFailure.DETAIL_API_FAILED,
            LinkImportFailure.DOWNLOAD_API_FAILED, LinkImportFailure.DOWNLOAD_FAILED);

    public record VideoProbe(double durationSeconds, int width, int height) {}

    public record DownloadedVideo(Path file, long size, String sha256Hex, String sourceUrl,
                                  String platform, String downloadMethod, String filename,
                                  VideoProbe probe) {}
    private record SharePage(URI uri, String body) {}

    private DouyinLinkVideoDownloader() {}

    public static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
    }

    /** 从粘贴的分享文本中取出第一个抖音链接，并校验它属于抖音。 */
    public static String normalizeShareUrl(String raw) {
        String url = extractDouyinLink(raw);
        if (url == null) {
            throw fail(LinkImportFailure.LINK_FORMAT,
                    "未在粘贴内容中找到链接，请粘贴包含 https://v.douyin.com/... 的分享内容。",
                    "no douyin link in pasted text");
        }
        if (url.length() > 1024) {
            throw fail(LinkImportFailure.LINK_FORMAT, "抖音视频链接过长，请使用分享短链。", "url too long");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw fail(LinkImportFailure.LINK_FORMAT, "请粘贴抖音视频链接。", "invalid uri");
        }
        if ("http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == -1) {
            uri = URI.create("https" + url.substring(url.indexOf(':')));
        }
        return requireDouyinUrl(uri, false).toString();
    }

    /**
     * 从粘贴的分享文案中提取第一个抖音视频链接（可带标题、话题与多余文字，也可省略 https://）。
     * 只认抖音域名；末尾的标点会被去掉，末尾的 / 保留（抖音短链本身以 / 结尾）。找不到时返回 null。
     */
    public static String extractDouyinLink(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = DOUYIN_LINK_PATTERN.matcher(raw);
        while (matcher.find()) {
            String found = matcher.group();
            while (!found.isEmpty() && ")]}，。；、！？!?,;:.（）【】《》#".indexOf(found.charAt(found.length() - 1)) >= 0) {
                found = found.substring(0, found.length() - 1);
            }
            if (found.isEmpty()) continue;
            String url = found.regionMatches(true, 0, "http", 0, 4) ? found : "https://" + found;
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_HOSTS.contains(host)) continue;
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) continue;
            return url;
        }
        return null;
    }

    static URI requireDouyinUrl(URI uri, boolean media) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = media ? MEDIA_DOMAINS.stream().anyMatch(domain -> host.equals(domain)
                || host.endsWith("." + domain)) : SUPPORTED_HOSTS.contains(host);
        if (!allowed) {
            if (media) {
                throw fail(LinkImportFailure.DOWNLOAD_FAILED,
                        "视频地址的主机 " + host + " 不在受支持的抖音媒体域名内，请上传本地视频。",
                        "rejected media host: " + host);
            }
            throw fail(LinkImportFailure.LINK_FORMAT,
                    "链接域名 " + host + " 不受支持，仅支持抖音视频链接（v.douyin.com 短链或 douyin.com/video/…）。",
                    "rejected link host: " + host);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw fail(media ? LinkImportFailure.DOWNLOAD_FAILED : LinkImportFailure.LINK_FORMAT,
                    media ? "视频地址无效，请稍后重试或上传本地视频。"
                            : "仅支持抖音视频链接（v.douyin.com 短链或 douyin.com/video/…）。",
                    "rejected url scheme/port/userinfo");
        }
        return uri;
    }

    private static HttpResponse<InputStream> sendDouyin(HttpClient http, URI uri, boolean media,
            Function<URI, HttpRequest> request) throws IOException, InterruptedException {
        for (int redirect = 0; redirect <= 5; redirect++) {
            requireDouyinUrl(uri, media);
            for (var address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                    throw fail(LinkImportFailure.LINK_FORMAT, "链接目标不是公网地址，请检查抖音视频链接。",
                            "private address: " + uri.getHost());
                }
            }
            var response = http.send(request.apply(uri), HttpResponse.BodyHandlers.ofInputStream());
            if (!REDIRECT_CODES.contains(response.statusCode())) return response;
            try (var body = response.body()) {
                String location = response.headers().firstValue("Location").orElseThrow(
                        () -> fail(LinkImportFailure.SHORT_LINK_RESOLVE,
                                "视频链接跳转地址不可用，请重新粘贴抖音视频链接。", "redirect without location"));
                try {
                    uri = uri.resolve(location);
                } catch (IllegalArgumentException ex) {
                    throw fail(LinkImportFailure.SHORT_LINK_RESOLVE,
                            "视频链接跳转地址无效，请重新粘贴抖音视频链接。", "bad redirect location: " + location);
                }
            }
        }
        throw fail(LinkImportFailure.SHORT_LINK_RESOLVE, "视频链接跳转次数过多，请重新粘贴抖音视频链接。", "too many redirects");
    }

    /** 从视频页链接中取出视频编号；短链解析前可能取不到，返回 null。 */
    public static String awemeId(String normalizedUrl) {
        if (normalizedUrl == null) return null;
        Matcher matcher = AWEME_PATTERN.matcher(normalizedUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 是否为抖音搜索页链接（/search/…），搜索页里没有单个视频地址。 */
    public static boolean isSearchPage(String normalizedUrl) {
        if (normalizedUrl == null) return false;
        try {
            String path = URI.create(normalizedUrl).getPath();
            return path != null && (path.equals("/search") || path.startsWith("/search/"));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 从搜索页链接中取出搜索弹窗里打开的视频编号（modal_id）；不是搜索页或没有 modal_id 时返回 null。
     * modal_id 是用户在搜索页打开的那条视频，只能把它当作同一条视频来尝试，不能据此拼凑其他视频。
     */
    public static String searchVideoId(String normalizedUrl) {
        if (!isSearchPage(normalizedUrl)) return null;
        String query;
        try {
            query = URI.create(normalizedUrl).getRawQuery();
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (query == null || query.isBlank()) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0 || !"modal_id".equals(pair.substring(0, eq))) continue;
            String value = pair.substring(eq + 1);
            try {
                value = URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return null;
            }
            return value.matches("\\d{6,20}") ? value : null;
        }
        return null;
    }

    /**
     * 按分享页或接口返回的文字判断视频状态；只有文字明确说明时才区分私密、已删除或需登录，
     * 否则返回 null（由调用方按“未找到视频信息”处理，不猜测）。
     */
    public static String classifyVideoState(String text) {
        if (text == null || text.isBlank()) return null;
        if (text.contains("need_login") || text.contains("login_required")
                || text.contains("请登录后查看") || text.contains("登录后查看") || text.contains("请先登录")) {
            return LinkImportFailure.LOGIN_REQUIRED;
        }
        if (text.contains("已被删除") || text.contains("视频不见了") || text.contains("视频不存在")
                || text.contains("该视频已删除")) {
            return LinkImportFailure.VIDEO_DELETED;
        }
        if (text.contains("仅自己可见") || text.contains("私密视频") || text.contains("仅作者可见")) {
            return LinkImportFailure.VIDEO_PRIVATE;
        }
        return null;
    }

    private static String messageForReason(String reason) {
        if (LinkImportFailure.VIDEO_PRIVATE.equals(reason)) {
            return "该视频为私密视频，仅作者自己可见，无法导入。请选择一条公开视频，或改用本地上传。";
        }
        if (LinkImportFailure.VIDEO_DELETED.equals(reason)) {
            return "该视频已被删除或不存在，请确认链接后重试，或选择其他公开视频。";
        }
        if (LinkImportFailure.LOGIN_REQUIRED.equals(reason)) {
            return "该视频需要登录后才能查看，无法导入。请确认链接指向的是公开视频（在无痕浏览器中打开能正常播放），或改用本地上传。";
        }
        if (LinkImportFailure.PLAY_URL_EMPTY.equals(reason)) {
            return "已找到该视频，但抖音未提供可下载的播放地址，请稍后再试或上传本地视频。";
        }
        return "未能从该链接解析出视频地址，请确认链接指向的是公开视频，或改用本地上传。";
    }

    /** aweme 详情接口地址：只用视频编号拼，不接受用户传入的主机或路径。 */
    public static String detailApiUrl(String awemeId) {
        return "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=" + awemeId
                + "&aid=1128&version_name=23.5.0&device_platform=webapp";
    }

    /**
     * 从详情接口返回的 JSON 中取出播放地址。返回的视频编号与请求的不一致时返回 null，
     * 绝不替用另一条视频。
     */
    public static String extractPlayUrlFromDetail(JsonNode root, String awemeId) {
        if (root == null) return null;
        JsonNode detail = root.path("aweme_detail");
        if (detail.isMissingNode() || detail.isNull() || !detail.isObject()) return null;
        String returned = detail.path("aweme_id").asText(null);
        if (awemeId == null || !awemeId.equals(returned)) return null;
        return findPlayUrl(detail);
    }

    /** 按详情接口的返回判断失败原因；未说明原因时返回 null，由调用方按播放地址缺失处理。 */
    public static String detailFailureReason(JsonNode root, String awemeId) {
        if (root == null) return LinkImportFailure.DETAIL_API_FAILED;
        String state = classifyVideoState(root.path("status_msg").asText(""));
        if (state != null) return state;
        if (root.path("status_code").asInt(0) != 0) return LinkImportFailure.PLAY_URL_EMPTY;
        JsonNode detail = root.path("aweme_detail");
        if (detail.isMissingNode() || detail.isNull()) return LinkImportFailure.PLAY_URL_EMPTY;
        String returned = detail.path("aweme_id").asText(null);
        if (awemeId == null || !awemeId.equals(returned)) return LinkImportFailure.PLAY_URL_EMPTY;
        return null;
    }

    static boolean hasTtwidCookie(CookieManager cookies) {
        if (cookies == null) return false;
        return cookies.getCookieStore().getCookies().stream()
                .anyMatch(cookie -> "ttwid".equalsIgnoreCase(cookie.getName()));
    }

    private static CookieManager cookieManager(HttpClient http) {
        return http.cookieHandler().filter(CookieManager.class::isInstance)
                .map(CookieManager.class::cast).orElse(null);
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
        String found = extractPlayUrlFromEscaped(html.replace("\\/", "/"), json);
        if (found != null) return found;
        try {
            String decoded = URLDecoder.decode(html, StandardCharsets.UTF_8).replace("\\/", "/");
            if (!decoded.equals(html)) return extractPlayUrlFromEscaped(decoded, json);
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
     * 主入口：先走 H5 路由；H5 路由失败且能确定视频编号时，先试 aweme 详情接口；
     * 再走已配置的备用接口；都失败时返回最明确的失败原因，不用其他视频替代。临时失败会把整条链路最多重试两次，每次间隔 2 秒。
     * 搜索页链接会先按 modal_id 尝试打开其中那条视频，失败时按搜索页报错。
     */
    public static DownloadedVideo download(ObjectMapper json, String rawInput,
        String downloadApiBaseUrl, String downloadApiPath, String downloadApiKey, String ffprobePath) {
        for (int attempt = 1; ; attempt++) {
            try {
                return downloadOnce(json, rawInput, downloadApiBaseUrl, downloadApiPath, downloadApiKey, ffprobePath);
            } catch (LinkImportFailure ex) {
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS || !RETRYABLE_FAILURES.contains(ex.reason())) throw ex;
                try { Thread.sleep(RETRY_DELAY_MILLIS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw ex; }
            }
        }
    }

    private static DownloadedVideo downloadOnce(ObjectMapper json, String rawInput,
            String downloadApiBaseUrl, String downloadApiPath, String downloadApiKey, String ffprobePath) {
        String normalized = normalizeShareUrl(rawInput);
        if (isSearchPage(normalized) && searchVideoId(normalized) == null) {
            throw fail(LinkImportFailure.SEARCH_PAGE,
                    "这是抖音搜索页链接，搜索页里没有单个视频地址，无法直接导入。请先打开要参考的视频，点分享复制它的视频链接（v.douyin.com 短链或 douyin.com/video/…），再粘贴到这里；也可以改用本地上传。",
                    "search page without modal_id");
        }
        String searchId = searchVideoId(normalized);
        String effectiveUrl = searchId != null ? "https://www.douyin.com/video/" + searchId : normalized;
        HttpClient http = httpClient();
        List<LinkImportFailure> failures = new ArrayList<>();
        try {
            return downloadViaH5(http, json, effectiveUrl, ffprobePath);
        } catch (LinkImportFailure ex) {
            if (isDefinitive(ex.reason())) throw ex;
            failures.add(ex);
        }
        String aweme = awemeId(effectiveUrl);
        if (aweme == null && !failures.isEmpty()) aweme = failures.get(0).awemeId();
        if (aweme != null && DETAIL_RETRYABLE.contains(failures.get(0).reason())) {
            try {
                return downloadViaDetailApi(http, json, aweme, ffprobePath);
            } catch (LinkImportFailure ex) {
                if (isDefinitive(ex.reason())) throw ex;
                failures.add(ex);
            }
        }
        String base = downloadApiBaseUrl == null ? "" : downloadApiBaseUrl.strip();
        String path = downloadApiPath == null ? "" : downloadApiPath.strip();
        boolean backupTried = false;
        if (!base.isBlank() && !path.isBlank()) {
            backupTried = true;
            try {
                return downloadViaDownloadApi(http, json, effectiveUrl, base, path, downloadApiKey, ffprobePath);
            } catch (LinkImportFailure ex) {
                failures.add(ex);
            }
        }
        if (searchId != null) throw searchPageFailure(searchId, failures);
        throw pickFailure(failures, backupTried);
    }

    private static DownloadedVideo downloadViaH5(HttpClient http, ObjectMapper json,
            String normalizedUrl, String ffprobePath) {
        URI shareUri = URI.create(normalizedUrl);
        String resolvedId = awemeId(normalizedUrl);
        SharePage first;
        try {
            first = fetchSharePage(http, mobileSharePage(shareUri));
        } catch (LinkImportFailure ex) {
            throw ex.withAwemeId(resolvedId);
        }
        if (resolvedId == null) resolvedId = awemeId(first.uri().getPath());
        SharePage second;
        try {
            second = fetchSharePage(http, mobileSharePage(first.uri()));
        } catch (LinkImportFailure ex) {
            throw ex.withAwemeId(resolvedId);
        }
        if (resolvedId == null) resolvedId = awemeId(second.uri().getPath());
        String html = second.body().isBlank() ? first.body() : second.body();
        boolean ttwidMissing = !hasTtwidCookie(cookieManager(http));
        if (html.contains("need_login")) {
            throw fail(LinkImportFailure.LOGIN_REQUIRED, messageForReason(LinkImportFailure.LOGIN_REQUIRED),
                    "share page need_login", resolvedId);
        }
        String state = classifyVideoState(html);
        if (state != null) {
            throw fail(state, messageForReason(state), "share page state signal: " + state, resolvedId);
        }
        String playUrl = extractPlayUrlOrWebPage(html, http, json, resolvedId);
        if (playUrl == null) {
            if (ttwidMissing) {
                throw fail(LinkImportFailure.TTWID_MISSING,
                        "未能获取抖音设备标识，分享页未返回完整内容。请稍后重试，或改用 v.douyin.com 短链重新粘贴。",
                        "ttwid missing after share page fetch", resolvedId);
            }
            if (html.contains("videoInfoRes")) {
                throw fail(LinkImportFailure.PLAY_URL_EMPTY, messageForReason(LinkImportFailure.PLAY_URL_EMPTY),
                        "videoInfoRes present but no play url", resolvedId);
            }
            throw fail(LinkImportFailure.VIDEOINFO_MISSING,
                    "分享页中未找到视频信息。请确认链接指向的是公开视频：先在无痕浏览器中打开该链接，能正常播放再粘贴；也可以改用 v.douyin.com 短链。",
                    "videoInfoRes missing", resolvedId);
        }
        try {
            Path file = downloadToTempFile(http, withoutWatermark(playUrl));
            return finish(file, normalizedUrl, PLATFORM_DOUYIN, METHOD_H5, json, ffprobePath);
        } catch (LinkImportFailure ex) {
            throw ex.withAwemeId(resolvedId);
        }
    }

    /**
     * aweme 详情接口兜底：用已确定的视频编号请求详情页，取 play_addr.url_list 的播放地址。
     * 只下载编号一致的那条视频；接口拒绝或编号不一致时明确报错，不替用其他视频。
     */
    private static DownloadedVideo downloadViaDetailApi(HttpClient http, ObjectMapper json,
            String awemeId, String ffprobePath) {
        HttpResponse<InputStream> response;
        try {
            response = sendDouyin(http, URI.create(detailApiUrl(awemeId)), false, target -> HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(15)).header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json").header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://www.douyin.com/").GET().build());
        } catch (LinkImportFailure ex) {
            throw fail(LinkImportFailure.DETAIL_API_FAILED, "抖音视频详情接口暂时无法访问，请稍后再试或上传本地视频。",
                    "detail api: " + ex.reason(), awemeId);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw fail(LinkImportFailure.DETAIL_API_FAILED, "抖音视频详情接口暂时无法访问，请稍后再试或上传本地视频。",
                    "detail api io: " + ex.getMessage(), awemeId);
        }
        byte[] body;
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw fail(LinkImportFailure.DETAIL_API_FAILED,
                        "抖音视频详情接口暂时无法访问（抖音返回 " + response.statusCode() + "），请稍后再试或上传本地视频。",
                        "detail api HTTP " + response.statusCode(), awemeId);
            }
            body = in.readNBytes(MAX_DETAIL_JSON_BYTES + 1);
        } catch (IOException ex) {
            throw fail(LinkImportFailure.DETAIL_API_FAILED, "抖音视频详情接口返回内容无法读取，请稍后再试或上传本地视频。",
                    "detail api read failed", awemeId);
        }
        if (body.length > MAX_DETAIL_JSON_BYTES) {
            throw fail(LinkImportFailure.DETAIL_API_FAILED, "抖音视频详情接口返回内容过大，暂时无法导入。",
                    "detail api too large", awemeId);
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception ex) {
            throw fail(LinkImportFailure.DETAIL_API_FAILED, "抖音视频详情接口返回内容无法解析，请稍后再试或上传本地视频。",
                    "detail api parse failed", awemeId);
        }
        String reason = detailFailureReason(root, awemeId);
        if (reason != null) {
            throw fail(reason, messageForReason(reason), "detail api: " + reason, awemeId);
        }
        String playUrl = extractPlayUrlFromDetail(root, awemeId);
        if (playUrl == null) {
            throw fail(LinkImportFailure.PLAY_URL_EMPTY, messageForReason(LinkImportFailure.PLAY_URL_EMPTY),
                    "detail api: no play url", awemeId);
        }
        Path file = downloadToTempFile(http, withoutWatermark(playUrl));
        return finish(file, "https://www.douyin.com/video/" + awemeId, PLATFORM_DOUYIN, METHOD_DETAIL_API, json, ffprobePath);
    }

    private static LinkImportFailure pickFailure(List<LinkImportFailure> failures, boolean backupTried) {
        LinkImportFailure chosen = null;
        for (LinkImportFailure failure : failures) {
            if (chosen == null) chosen = failure;
            if (isDefinitive(failure.reason())) {
                chosen = failure;
                break;
            }
        }
        if (chosen == null) throw new IllegalStateException("no link failure recorded");
        if (!backupTried || LinkImportFailure.SPEC_FAILED.equals(chosen.reason())
                || LinkImportFailure.VIDEO_TOO_LARGE.equals(chosen.reason())) {
            return chosen;
        }
        return new LinkImportFailure(chosen.reason(),
                chosen.getMessage() + "备用下载接口也未能获取该视频，请稍后再试或上传本地视频。",
                chosen.detail(), chosen.awemeId());
    }

    private static boolean isDefinitive(String reason) {
        return LinkImportFailure.VIDEO_PRIVATE.equals(reason) || LinkImportFailure.VIDEO_DELETED.equals(reason)
                || LinkImportFailure.LOGIN_REQUIRED.equals(reason) || LinkImportFailure.SPEC_FAILED.equals(reason)
                || LinkImportFailure.VIDEO_TOO_LARGE.equals(reason);
    }

    private static LinkImportFailure searchPageFailure(String searchId, List<LinkImportFailure> failures) {
        List<String> reasons = new ArrayList<>();
        for (LinkImportFailure failure : failures) {
            if (!reasons.contains(failure.reason())) reasons.add(failure.reason());
        }
        return fail(LinkImportFailure.SEARCH_PAGE,
                "这是抖音搜索页链接，搜索页里没有单个视频地址，无法直接导入。已尝试打开搜索页中打开的视频，仍未能获取该视频。"
                        + "请先打开要参考的视频，点分享复制它的视频链接（v.douyin.com 短链或 douyin.com/video/…），再粘贴到这里；也可以改用本地上传。",
                "search modal_id=" + searchId + "; attempts=" + String.join(",", reasons));
    }

    private static URI mobileSharePage(URI uri) {
        String id = awemeId(uri.getPath());
        return id == null ? uri : URI.create("https://www.iesdouyin.com/share/video/" + id + "/");
    }

    private static SharePage fetchSharePage(HttpClient http, URI uri) {
        try {
            var response = sendDouyin(http, uri, false, url -> HttpRequest.newBuilder(url)
                    .timeout(Duration.ofSeconds(15)).header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://www.douyin.com/").GET().build());
            try (var body = response.body()) {
                int status = response.statusCode();
                if (status == 404) {
                    throw fail(LinkImportFailure.VIDEO_DELETED, messageForReason(LinkImportFailure.VIDEO_DELETED),
                            "share page HTTP 404");
                }
                if (status == 403 || status == 429) {
                    throw fail(LinkImportFailure.SHARE_PAGE_FETCH, "抖音暂时拒绝了本次访问，请稍后再试或上传本地视频。",
                            "share page HTTP " + status);
                }
                if (status < 200 || status >= 300) {
                    throw fail(LinkImportFailure.SHARE_PAGE_FETCH,
                            "链接暂时无法访问（抖音返回 " + status + "），请稍后再试或上传本地视频。",
                            "share page HTTP " + status);
                }
                byte[] bytes = body.readNBytes(MAX_SHARE_PAGE_BYTES + 1);
                if (bytes.length > MAX_SHARE_PAGE_BYTES) {
                    throw fail(LinkImportFailure.SHARE_PAGE_FETCH, "抖音分享页内容过大，暂时无法导入。", "share page too large");
                }
                return new SharePage(response.uri(), new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw fail(LinkImportFailure.SHARE_PAGE_FETCH, "链接访问被中断，请稍后重试。", "share page interrupted");
        } catch (IOException ex) {
            throw fail(LinkImportFailure.SHARE_PAGE_FETCH, "链接暂时无法访问，请检查链接后重试。", "share page io: " + ex.getMessage());
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
            throw fail(LinkImportFailure.DOWNLOAD_API_FAILED, "备用下载接口访问被中断，请稍后重试。", "download api interrupted");
        } catch (IOException ex) {
            throw fail(LinkImportFailure.DOWNLOAD_API_FAILED, "备用下载接口暂时无法访问，未能获取该视频。",
                    "download api io: " + ex.getMessage());
        }
        if (response.statusCode() != 200 && response.statusCode() != 202) {
        throw fail(LinkImportFailure.DOWNLOAD_API_FAILED, "备用下载接口返回 " + response.statusCode() + "，未能获取该视频。",
                    "download api HTTP " + response.statusCode());
        }
        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (Exception ex) {
            throw fail(LinkImportFailure.DOWNLOAD_API_FAILED, "备用下载接口返回内容无法解析，未能获取该视频。", "download api parse failed");
        }
        String playUrl = findVideoUrl(root);
    if (playUrl == null) {
        String taskId = DownloadApiTaskPoller.taskId(root);
        if (taskId != null) {
            playUrl = DownloadApiTaskPoller.pollForPlayUrl(http, json, base, apiKey, taskId);
        }
    }
        if (playUrl == null) {
            throw fail(LinkImportFailure.DOWNLOAD_API_FAILED, "备用下载接口未返回视频地址，未能获取该视频。", "download api no play url");
        }
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
            throw fail(LinkImportFailure.DOWNLOAD_FAILED, "服务器暂时无法保存链接视频，请稍后重试。", "create temp file failed");
        }
        try {
            var response = sendDouyin(http, URI.create(playUrl), true, url -> HttpRequest.newBuilder(url)
                    .timeout(Duration.ofSeconds(120)).header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.iesdouyin.com/").GET().build());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw fail(LinkImportFailure.DOWNLOAD_FAILED,
                            "链接视频暂时无法下载（视频地址返回 " + response.statusCode() + "），请稍后再试或上传本地视频。",
                            "media HTTP " + response.statusCode());
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (contentType.contains("text/html")) {
                    String page = new String(in.readNBytes(MAX_SHARE_PAGE_BYTES + 1), StandardCharsets.UTF_8);
                    String state = classifyVideoState(page);
                    if (state != null) {
                        throw fail(state, messageForReason(state), "media url returned html: " + state);
                    }
                    throw fail(LinkImportFailure.DOWNLOAD_FAILED, "下载到的不是视频文件，该视频可能为私密、已删除或需要登录。",
                            "media url returned html");
                }
                try (var out = Files.newOutputStream(temp)) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_BYTES) {
                            throw fail(LinkImportFailure.VIDEO_TOO_LARGE, "链接视频超过 200 MB，请上传本地视频。", "media size exceeded");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
            return temp;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            deleteQuietly(temp);
            throw fail(LinkImportFailure.DOWNLOAD_FAILED, "链接视频下载被中断，请稍后重试。", "media download interrupted");
        } catch (LinkImportFailure ex) {
            deleteQuietly(temp);
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            deleteQuietly(temp);
            throw fail(LinkImportFailure.DOWNLOAD_FAILED, "链接视频暂时无法下载，请稍后再试或上传本地视频。",
                    "media download io: " + ex.getMessage());
        }
    }

    private static DownloadedVideo finish(Path file, String sourceUrl, String platform, String method,
            ObjectMapper json, String ffprobePath) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            deleteQuietly(file);
            throw fail(LinkImportFailure.DOWNLOAD_FAILED, "下载到的视频文件无法读取，请稍后重试。", "read downloaded file failed");
        }
        if (size <= 0) {
            deleteQuietly(file);
            throw fail(LinkImportFailure.DOWNLOAD_FAILED, "下载到的视频文件为空，请确认链接指向的是有效视频。", "downloaded file empty");
        }
        if (size > MAX_BYTES) {
            deleteQuietly(file);
            throw fail(LinkImportFailure.VIDEO_TOO_LARGE, "链接视频超过 200 MB，请上传本地视频。", "downloaded size=" + size);
        }
        try {
            String sha256 = sha256Hex(file);
            VideoProbe probe = probe(file, ffprobePath, json);
            String aweme = awemeId(sourceUrl);
            String filename = "douyin-" + (aweme == null ? "video" : aweme) + ".mp4";
            return new DownloadedVideo(file, size, sha256, sourceUrl, platform, method, filename, probe);
        } catch (RuntimeException ex) {
            deleteQuietly(file);
            throw ex;
        }
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
            throw fail(LinkImportFailure.DOWNLOAD_FAILED, "无法校验下载到的视频文件，请稍后重试。", "sha256 failed");
        }
    }

    /**
     * 用 ffprobe 读取真实视频流并校验规格；与前端本地上传的校验口径一致
     * （4–120 秒、横屏 16:9 或竖屏 9:16、短边 720–1440 像素、长边不超过 2560 像素）。
     * ffprobe 不可用或校验失败时明确报错，不放行未校验的视频。
     */
    public static VideoProbe probe(Path file, String ffprobePath, ObjectMapper json) {
        String command = ffprobePath == null || ffprobePath.isBlank() ? "ffprobe" : ffprobePath.strip();
        if (file == null || !Files.isRegularFile(file)) {
            throw fail(LinkImportFailure.DOWNLOAD_FAILED, "链接视频文件缺失，请重新粘贴链接。", "file missing");
        }
        Process process;
        try {
            process = new ProcessBuilder(command, "-v", "error", "-protocol_whitelist", "file,pipe",
                    "-f", "mov", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height,duration",
                    "-show_entries", "format=duration", "-of", "json", file.toString())
                    .redirectErrorStream(true).start();
        } catch (IOException ex) {
            throw fail(LinkImportFailure.FFPROBE_UNAVAILABLE, "服务器未安装 ffprobe，暂时无法校验链接视频，请稍后重试或上传本地视频。", "ffprobe start failed");
        }
        boolean finished;
        try {
            finished = process.waitFor(60, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw fail(LinkImportFailure.SPEC_FAILED, "视频校验超时，请稍后重试。", "ffprobe timeout");
        }
        if (!finished) {
            process.destroyForcibly();
            throw fail(LinkImportFailure.SPEC_FAILED, "视频校验超时，请稍后重试。", "ffprobe timeout");
        }
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw fail(LinkImportFailure.SPEC_FAILED, "无法读取视频校验结果，请稍后重试。", "ffprobe read failed");
        }
        if (process.exitValue() != 0) {
            throw fail(LinkImportFailure.SPEC_FAILED, "无法校验该视频，请确认链接指向的是有效的视频文件。", "ffprobe exit=" + process.exitValue());
        }
        JsonNode root;
        try {
            root = json.readTree(output);
        } catch (Exception ex) {
            throw fail(LinkImportFailure.SPEC_FAILED, "无法校验该视频，请确认链接指向的是有效的视频文件。", "ffprobe parse failed");
        }
        JsonNode stream = root.path("streams").path(0);
        int width = stream.path("width").asInt(0);
        int height = stream.path("height").asInt(0);
        double duration = stream.path("duration").asDouble(Double.NaN);
        if (Double.isNaN(duration)) duration = root.path("format").path("duration").asDouble(Double.NaN);
        if (width <= 0 || height <= 0 || Double.isNaN(duration) || !Double.isFinite(duration)) {
            throw fail(LinkImportFailure.SPEC_FAILED, "无法读取该视频的时长与尺寸，请确认链接指向的是有效的视频文件。", "ffprobe no stream info");
        }
        return validateSpec(duration, width, height);
    }

    public static VideoProbe validateSpec(double durationSeconds, int width, int height) {
        if (durationSeconds < 3.98 || durationSeconds > 120.02) {
            throw fail(LinkImportFailure.SPEC_FAILED, "参考视频须为 4–120 秒。", "duration=" + durationSeconds);
        }
        int shortEdge = Math.min(width, height);
        int longEdge = Math.max(width, height);
        if (shortEdge < 720 || shortEdge > 1440 || longEdge > 2560) {
            throw fail(LinkImportFailure.SPEC_FAILED, "参考视频短边须为 720–1440 像素，长边不超过 2560 像素。", width + "x" + height);
        }
        if (Math.abs((double) longEdge / shortEdge - 16.0 / 9.0) > 0.005) {
            throw fail(LinkImportFailure.SPEC_FAILED, "参考视频须为横屏 16:9 或竖屏 9:16。", width + "x" + height);
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

    private static LinkImportFailure fail(String reason, String message) {
        return new LinkImportFailure(reason, message, null);
    }

    private static LinkImportFailure fail(String reason, String message, String detail) {
        return new LinkImportFailure(reason, message, detail);
    }

    private static LinkImportFailure fail(String reason, String message, String detail, String awemeId) {
        return new LinkImportFailure(reason, message, detail, awemeId);
    }

    // www.douyin.com 视频页直链前缀：iesdouyin 分享页现会 302 跳转到该页，
    // 分享页路由拿到的本来就是这份 HTML；解析不到播放地址时再直取一次该页，
    // 既少一次跳转，也避开跳转带上的 previous_page 等参数导致的不同页面形态。
    private static final String WEB_VIDEO_URL_PREFIX = "https://www.douyin.com/video/";

    /**
     * 先按原有逻辑从分享页 HTML 提取播放地址；取不到且视频编号已知时，
     * 再直取 www.douyin.com 视频页并同样提取一次。直取失败返回 null，
     * 由调用方按原有原因码（VIDEOINFO_MISSING 等）报错，不改变错误面。
     */
    static String extractPlayUrlOrWebPage(String html, HttpClient http, ObjectMapper json, String resolvedId) {
        String found = extractPlayUrl(html, json);
        if (found != null || resolvedId == null || resolvedId.isBlank()) {
            return found;
        }
        try {
            URI webUri = requireDouyinUrl(URI.create(WEB_VIDEO_URL_PREFIX + resolvedId), false);
            SharePage page = fetchSharePage(http, webUri);
            return extractPlayUrl(page.body(), json);
        } catch (RuntimeException ignored) {
            // www 视频页也没能取到可用内容：返回 null，让调用方报原有的错。
            return null;
        }
    }

    /**
     * 从分享页 HTML 中提取播放地址（增强版）。
     * www.douyin.com 视频页把视频数据嵌在 JS 字符串字面量（如 RSC self.__next_f）
     * 里，JSON 会被双重转义，原有提取逻辑只能处理单层转义斜杠与 URL 编码，
     * 会全部错过；这里先做多轮反转义再提取，并在 videoInfoRes 不可用时直接从
     * play_addr 对象取播放地址。
     */
    static String extractPlayUrlFromEscaped(String html, ObjectMapper json) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String found = extractPlayUrlFromText(html.replace("\\/", "/"), json);
        if (found != null) {
            return found;
        }
        found = extractFromVideoInfoString(html, json);
        if (found != null) {
            return found;
        }
        String unescaped = unescapeEmbeddedJson(html);
        if (!unescaped.equals(html)) {
            found = extractPlayUrlFromText(unescaped, json);
            if (found != null) {
                return found;
            }
        }
        return extractFromPlayAddr(unescaped, json);
    }

    /**
     * 反转义页面内嵌的 JSON 字符串：多轮处理转义斜杠、转义引号、转义反斜杠与
     * unicode 转义，直到稳定或达到轮数上限；输入已是普通文本时原样返回。
     */
    static String unescapeEmbeddedJson(String text) {
        String current = text;
        for (int round = 0; round < 4; round++) {
            String next = unescapeEmbeddedJsonOnce(current);
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        return current;
    }

    static String unescapeEmbeddedJsonOnce(String text) {
        String result = text.replace("\\\\", "\\").replace("\\/", "/").replace("\\\"", "\"");
        return decodeUnicodeEscapes(result);
    }

    static String decodeUnicodeEscapes(String text) {
        if (text.indexOf("\\u") < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 5 < text.length() && text.charAt(i + 1) == 'u') {
                int code = parseHex4(text, i + 2);
                if (code >= 0) {
                    out.append((char) code);
                    i += 5;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    static int parseHex4(String text, int from) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(from + i), 16);
            if (digit < 0) {
                return -1;
            }
            value = value * 16 + digit;
        }
        return value;
    }

    /**
     * 直接从 play_addr 对象取播放地址：即使外层 videoInfoRes 被改名或移走，
     * 只要播放地址对象还在，就能拿到无水印播放地址。
     */
    static String extractFromPlayAddr(String text, ObjectMapper json) {
        int searchFrom = 0;
        while (true) {
            int key = text.indexOf("\"play_addr\"", searchFrom);
            if (key < 0) {
                return null;
            }
            int brace = text.indexOf('{', key);
            if (brace < 0) {
                return null;
            }
            String object = balancedObject(text, brace);
            if (object != null) {
                try {
                    JsonNode playAddr = json.readTree(object);
                    JsonNode urlList = playAddr.path("url_list");
                    if (urlList.isArray()) {
                        for (JsonNode url : urlList) {
                            if (url.isTextual() && !url.asText().isBlank()) {
                                return url.asText();
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 不是合法 JSON 对象，继续找下一个 play_addr。
                }
            }
            searchFrom = brace + 1;
        }
    }

    /**
     * 处理 videoInfoRes 的值本身就是一段 JSON 字符串的情况
     * （外层载荷再包一层字符串时会出现）：解开字符串后再解析取播放地址。
     */
    static String extractFromVideoInfoString(String text, ObjectMapper json) {
        int searchFrom = 0;
        while (true) {
            int key = text.indexOf("\"videoInfoRes\"", searchFrom);
            if (key < 0) {
                return null;
            }
            int colon = text.indexOf(':', key + 14);
            if (colon < 0) {
                return null;
            }
            int value = colon + 1;
            while (value < text.length() && Character.isWhitespace(text.charAt(value))) {
                value++;
            }
            if (value >= text.length()) {
                return null;
            }
            if (text.charAt(value) == '"') {
                String inner = quotedString(text, value);
                if (inner != null) {
                    try {
                        String found = findPlayUrl(json.readTree(unescapeEmbeddedJson(inner)));
                        if (found != null) {
                            return found;
                        }
                    } catch (Exception ignored) {
                        // 字符串里不是合法 JSON，继续找下一个 videoInfoRes。
                    }
                }
            }
            searchFrom = value + 1;
        }
    }

    static String quotedString(String text, int openQuote) {
        StringBuilder out = new StringBuilder();
        for (int i = openQuote + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (c == '"') {
                return out.toString();
            }
            out.append(c);
        }
        return null;
    }
}
