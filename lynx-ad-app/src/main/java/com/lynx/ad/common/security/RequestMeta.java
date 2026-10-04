package com.lynx.ad.common.security;

import java.util.Optional;

/** 当前请求的来源地址与 UA，供审计埋点读取；登录等无 JWT 的路径也可写入。 */
public final class RequestMeta {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private RequestMeta() {}

    public static void set(String ip, String userAgent) {
        HOLDER.set(new Snapshot(ip, userAgent));
    }

    public static Optional<String> ip() {
        Snapshot s = HOLDER.get();
        return s == null ? Optional.empty() : Optional.ofNullable(s.ip);
    }

    public static Optional<String> userAgent() {
        Snapshot s = HOLDER.get();
        return s == null ? Optional.empty() : Optional.ofNullable(s.userAgent);
    }

    public static void clear() {
        HOLDER.remove();
    }

    private record Snapshot(String ip, String userAgent) {}
}
