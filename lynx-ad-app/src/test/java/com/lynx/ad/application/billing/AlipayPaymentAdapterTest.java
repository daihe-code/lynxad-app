package com.lynx.ad.application.billing;

import static org.junit.jupiter.api.Assertions.*;

import com.alipay.api.internal.util.AlipaySignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.domain.model.BillingPayment;
import com.lynx.ad.modules.billing.domain.model.PayChannel;
import com.lynx.ad.modules.billing.domain.model.PaymentStatus;
import com.lynx.ad.modules.billing.infrastructure.adapter.AlipayPaymentAdapter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AlipayPaymentAdapterTest {
    private static final String PAYMENT_ID = "ali_0123456789abcdef0123456789abcdef";
    private static final String APP_ID = "2021000000000000";
    private static final String SELLER_ID = "2088000000000000";
    private static final String NOTIFY_URL = "https://checkout.example.com/lynx-ad/api/v1/billing/payments/alipay/notify";
    private static KeyPair merchant;
    private static KeyPair platform;

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        merchant = generator.generateKeyPair();
        platform = generator.generateKeyPair();
    }

    @Test
    void officialCheckoutUsesTheStoredAmountAndOrderNumber() throws Exception {
        var payment = new BillingPayment(PAYMENT_ID, new TenantId("workspace"), "order", PayChannel.ALIPAY,
                PaymentStatus.CREATED, 9900, null, null, Instant.parse("2026-11-17T02:00:00Z"), null);
        var url = URI.create(adapter().checkoutUrl(payment, "坤境智营积分充值", true));
        assertEquals("https", url.getScheme());
        assertEquals("openapi.alipay.com", url.getHost());
        assertEquals("/gateway.do", url.getPath());
        var params = new HashMap<String, String>();
        for (String pair : url.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        assertEquals("alipay.trade.page.pay", params.get("method"));
        assertEquals(NOTIFY_URL, params.get("notify_url"));
        assertEquals("https://checkout.example.com/accounts?tab=workspace", params.get("return_url"));
        assertNotNull(params.get("sign"));
        var order = new ObjectMapper().readTree(params.get("biz_content"));
        assertEquals(PAYMENT_ID, order.path("out_trade_no").asText());
        assertEquals("99.00", order.path("total_amount").asText());
        assertEquals("2", order.path("qr_pay_mode").asText());
        assertEquals("2026-11-17 10:30:00", order.path("time_expire").asText());
    }

    @Test
    void acceptsSignedFormAndRejectsTamperingAndOtherMerchants() throws Exception {
        var adapter = adapter();
        var fields = signedForm(SELLER_ID);
        var trade = adapter.notification(fields);
        assertEquals(AlipayPaymentAdapter.Status.PAID, trade.status());
        assertEquals(9900, trade.amountFen());
        assertEquals("authenticated-trade", trade.tradeNo());
        fields.put("total_amount", "0.01");
        assertThrows(BizException.class, () -> adapter.notification(fields));
        assertThrows(BizException.class, () -> adapter.notification(signedForm("2088000000000001")));
    }

    private static Map<String, String> signedForm(String sellerId) throws Exception {
        var fields = new HashMap<>(Map.of("app_id", APP_ID, "seller_id", sellerId, "out_trade_no", PAYMENT_ID,
                "trade_status", "TRADE_SUCCESS", "trade_no", "authenticated-trade", "total_amount", "99.00"));
        String sign = AlipaySignature.rsa256Sign(
                AlipaySignature.getSignContent(fields), Base64.getEncoder().encodeToString(platform.getPrivate().getEncoded()), "UTF-8");
        fields.put("sign_type", "RSA2");
        fields.put("sign", sign);
        return fields;
    }

    private static AlipayPaymentAdapter adapter() {
        var properties = new LynxAdProperties();
        var config = properties.getPayment().getAlipay();
        config.setAppId(APP_ID);
        config.setSellerId(SELLER_ID);
        config.setPrivateKey(Base64.getEncoder().encodeToString(merchant.getPrivate().getEncoded()));
        config.setPublicKey(Base64.getEncoder().encodeToString(platform.getPublic().getEncoded()));
        config.setNotifyUrl(NOTIFY_URL);
        config.setReturnOrigin("https://checkout.example.com");
        return new AlipayPaymentAdapter(properties);
    }
}
