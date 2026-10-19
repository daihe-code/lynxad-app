package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.identity.api.IdentityOauthController;
import com.lynx.ad.modules.identity.api.OauthRelayPage;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class OauthRelayPageTest {

    private static final String BASE = "http://localhost:8080/lynx-ad/api/v1/identity/oauth/callback";

    @Test
    void showsDecodedParamsAndRawQueryOnConfiguredBase() {
        String html = OauthRelayPage.render("auth_code=code-1&state=ab%2Bcd", BASE);
        assertTrue(html.contains("千川回调参数"));
        assertTrue(html.contains("auth_code = code-1"));
        assertTrue(html.contains("state = ab+cd"));
        assertTrue(html.contains(BASE + "?auth_code=code-1&amp;state=ab%2Bcd"));
        assertTrue(html.contains("复制完整地址"));
        assertFalse(html.contains("未收到回调参数"));
    }

    @Test
    void escapesMarkupInDisplayedValues() {
        String html = OauthRelayPage.render("auth_code=%3Cscript%3Ealert(1)%3C/script%3E&state=%22onclick%3Dalert(1)", BASE);
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("&quot;onclick=alert(1)"));
        assertFalse(html.contains("<script>alert"));
        assertFalse(html.contains("\"onclick"));
        assertTrue(html.contains("auth_code=%3Cscript%3Ealert(1)%3C/script%3E"));
    }

    @Test
    void emptyQueryDoesNotAppendQuestionMark() {
        String html = OauthRelayPage.render(null, BASE);
        assertTrue(html.contains("未收到回调参数"));
        assertFalse(html.contains("完整地址"));
        assertFalse(html.contains(BASE));
    }

    @Test
    void blankBaseDoesNotConcatenate() {
        String html = OauthRelayPage.render("auth_code=x", "  ");
        assertTrue(html.contains("auth_code = x"));
        assertTrue(html.contains("未配置 QIANCHUAN_REDIRECT_URI"));
        assertFalse(html.contains("完整地址"));
    }

    @Test
    void appendsWithAmpersandWhenBaseAlreadyHasQuery() {
        String html = OauthRelayPage.render("auth_code=x", BASE + "?from=relay");
        assertTrue(html.contains(BASE + "?from=relay&amp;auth_code=x"));
    }

    @Test
    void overlongQueryIsNotEchoed() {
        String html = OauthRelayPage.render("a=" + "b".repeat(9000), BASE);
        assertTrue(html.contains("查询串过长"));
        assertFalse(html.contains("完整地址"));
        assertFalse(html.contains("bbbbbbbb"));
        assertFalse(html.contains(BASE));
    }

    @Test
    void authorizeUrlUsesPlatformRedirectOnlyWhenSet() {
        LynxAdProperties.Qianchuan qc = new LynxAdProperties.Qianchuan();
        qc.setRedirectUri(BASE);
        assertEquals(BASE, qc.authorizeRedirectUri());
        qc.setPlatformRedirectUri("  ");
        assertEquals(BASE, qc.authorizeRedirectUri());
        qc.setPlatformRedirectUri("https://lynx-ad.com/lynx-ad/api/v1/identity/oauth/relay");
        assertEquals("https://lynx-ad.com/lynx-ad/api/v1/identity/oauth/relay", qc.authorizeRedirectUri());
    }

    @Test
    void relayPathIsNotCountedAsExchangeCallback() throws Exception {
        int callbacks = 0;
        boolean relay = false;
        for (Method method : IdentityOauthController.class.getDeclaredMethods()) {
            GetMapping get = method.getAnnotation(GetMapping.class);
            if (get == null) {
                continue;
            }
            for (String path : get.value()) {
                if (path.contains("callback")) {
                    callbacks++;
                    assertEquals("/api/v1/identity/oauth/callback", path);
                }
                if ("/api/v1/identity/oauth/relay".equals(path)) {
                    relay = true;
                }
            }
        }
        assertEquals(1, callbacks);
        assertTrue(relay);
    }
}
