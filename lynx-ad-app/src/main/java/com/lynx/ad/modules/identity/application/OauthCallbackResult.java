package com.lynx.ad.modules.identity.application;

public record OauthCallbackResult(boolean ok, String message) {

    public static OauthCallbackResult success() {
        return new OauthCallbackResult(true, null);
    }

    public static OauthCallbackResult fail(String message) {
        return new OauthCallbackResult(false, message == null || message.isBlank() ? "授权失败" : message);
    }
}
