package com.lynx.ad.modules.identity.api;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 千川公网回调的展示页。只拼地址，不换票。 */
public final class OauthRelayPage {

    static final int MAX_QUERY_BYTES = 8192;

    private OauthRelayPage() {}

    public static String render(String rawQuery, String redirectUri) {
        boolean tooLong = rawQuery != null && rawQuery.getBytes(StandardCharsets.UTF_8).length > MAX_QUERY_BYTES;
        boolean hasQuery = rawQuery != null && !rawQuery.isBlank();
        String base = redirectUri == null ? "" : redirectUri.trim();
        boolean hasBase = !base.isEmpty();
        String fullUrl = !tooLong && hasQuery && hasBase ? join(base, rawQuery) : "";
        StringBuilder body = new StringBuilder();
        body.append("<h1>千川回调参数</h1>");
        if (tooLong) {
            body.append("<p>查询串过长</p>");
        } else {
            if (!hasQuery) {
                body.append("<p>未收到回调参数</p>");
            } else {
                for (Param param : parse(rawQuery)) {
                    body.append("<p class=\"param\">")
                            .append(esc(param.key()))
                            .append(" = ")
                            .append(esc(param.value()))
                            .append("</p>");
                }
            }
            if (!hasBase) {
                body.append("<p>未配置 QIANCHUAN_REDIRECT_URI</p>");
            } else if (hasQuery) {
                body.append("<p>完整地址</p>");
                body.append("<p class=\"url\" id=\"full-url\" data-url=\"")
                        .append(esc(fullUrl))
                        .append("\">")
                        .append(esc(fullUrl))
                        .append("</p>");
                body.append("<button type=\"button\" onclick=\"copyUrl()\">复制完整地址</button>");
            }
        }
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>千川回调参数</title>
                <style>
                  html, body { height: 100%; margin: 0; }
                  body { display: flex; align-items: center; justify-content: center; font-family: sans-serif; }
                  main { text-align: center; max-width: 40rem; padding: 1.5rem; }
                  .param, .url { word-break: break-all; font-family: ui-monospace, monospace; }
                </style>
                </head>
                <body>
                <main>
                """
                + body
                + """
                </main>
                <script>
                function copyUrl() {
                  var el = document.getElementById('full-url');
                  if (!el) return;
                  var text = el.getAttribute('data-url') || '';
                  if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).catch(function () { selectUrl(el); });
                  } else {
                    selectUrl(el);
                  }
                }
                function selectUrl(el) {
                  var range = document.createRange();
                  range.selectNodeContents(el);
                  var sel = window.getSelection();
                  sel.removeAllRanges();
                  sel.addRange(range);
                }
                </script>
                </body>
                </html>
                """;
    }

    private static String join(String base, String rawQuery) {
        return base.contains("?") ? base + "&" + rawQuery : base + "?" + rawQuery;
    }

    private static List<Param> parse(String rawQuery) {
        List<Param> out = new ArrayList<>();
        for (String part : rawQuery.split("&", -1)) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = eq < 0 ? part : part.substring(0, eq);
            String value = eq < 0 ? "" : part.substring(eq + 1);
            out.add(new Param(decode(key), decode(value)));
        }
        return out;
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return raw;
        }
    }

    private static String esc(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record Param(String key, String value) {}
}
