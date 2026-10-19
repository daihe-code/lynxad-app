package com.lynx.ad.modules.identity.application;

public final class DemoLiveCredentialRules {

    private DemoLiveCredentialRules() {}

    public static boolean isDemoAdvertiser(String advertiserId) {
        return "adv_demo".equals(advertiserId) || "adv_oauth_mock".equals(advertiserId);
    }

    public static boolean isDemoAccessToken(String plain) {
        if (plain == null) {
            return false;
        }
        return "mock-access".equals(plain)
                || "callback-access".equals(plain)
                || plain.startsWith("callback-access-refreshed-");
    }
}
