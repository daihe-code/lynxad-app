package com.lynx.ad.common.http;

import com.lynx.ad.common.exception.BizException;

public final class Qianchuan401ReplayCodes {

    private Qianchuan401ReplayCodes() {}

    public static boolean isAuthExpiredCode(int code) {
        return code == 401 || code == 40102 || code == 40105;
    }

    public static boolean isAuthExpired(BizException ex) {
        return isAuthExpiredCode(ex.code());
    }
}
