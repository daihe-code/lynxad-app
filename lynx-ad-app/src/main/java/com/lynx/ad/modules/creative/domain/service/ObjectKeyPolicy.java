package com.lynx.ad.modules.creative.domain.service;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class ObjectKeyPolicy {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ObjectKeyPolicy() {}

    public static String nextKey(String prefix, TenantId tenant, String filename) {
        String p = prefix == null || prefix.isBlank() ? "lynx-ad/" : prefix;
        if (!p.endsWith("/")) {
            p = p + "/";
        }
        String day = LocalDate.now(SHANGHAI).format(DAY);
        return p + tenant.value() + "/creative/" + day + "/" + UUID.randomUUID() + "_" + safeFilename(filename);
    }

    public static String safeFilename(String filename) {
        String raw = filename == null || filename.isBlank() ? "file.bin" : filename;
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (base.contains("..")) {
            throw new BizException(1510, "文件名不合法");
        }
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank()) {
            cleaned = "file.bin";
        }
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    public static void assertTenantKey(String prefix, TenantId tenant, String objectKey) {
        String p = prefix == null || prefix.isBlank() ? "lynx-ad/" : prefix;
        if (!p.endsWith("/")) {
            p = p + "/";
        }
        String expected = p + tenant.value() + "/";
        if (objectKey == null || objectKey.contains("..") || !objectKey.toLowerCase(Locale.ROOT).startsWith(expected.toLowerCase(Locale.ROOT))) {
            throw new BizException(1508, "媒体令牌无效或过期");
        }
    }
}
