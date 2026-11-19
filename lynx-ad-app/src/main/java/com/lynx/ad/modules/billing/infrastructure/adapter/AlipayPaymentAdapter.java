package com.lynx.ad.modules.billing.infrastructure.adapter;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.domain.model.BillingPayment;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AlipayPaymentAdapter {
    private static final String CHARSET = "UTF-8";
    private static final String SIGN_TYPE = "RSA2";
    private static final Duration CHECKOUT_DURATION = Duration.ofMinutes(30);
    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private static final Set<String> GATEWAYS = Set.of(
            "openapi.alipay.com", "openapi.alipaydev.com", "openapi-sandbox.dl.alipaydev.com");

    public enum Status { NOT_FOUND, WAITING, PAID, CLOSED }

    public record Trade(String paymentId, Status status, String tradeNo, Integer amountFen) {}

    private final LynxAdProperties.Alipay config;
    private final boolean enabled;
    private AlipayClient client;

    public AlipayPaymentAdapter(LynxAdProperties properties) {
        this.config = properties.getPayment().getAlipay();
        this.enabled = properties.getPayment().isAlipay();
    }

    @PostConstruct
    void validateConfiguration() {
        if (!enabled) return;
        try {
            client();
            AlipaySignature.rsa256Sign("payment-configuration-check", key(config.getPrivateKey()), CHARSET);
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝支付加签配置无效");
        }
    }

    public boolean expired(BillingPayment payment, Instant now) {
        return !now.isBefore(checkoutExpiresAt(payment));
    }

    public Instant checkoutExpiresAt(BillingPayment payment) {
        return payment.createdAt().plus(CHECKOUT_DURATION);
    }

    public String checkoutUrl(BillingPayment payment, String subject, boolean recharge) {
        if (payment.amountFen() <= 0) throw new BizException(1603, "订单应付金额无效");
        var model = new AlipayTradePagePayModel();
        model.setOutTradeNo(payment.id());
        model.setTotalAmount(BigDecimal.valueOf(payment.amountFen(), 2).toPlainString());
        model.setSubject(subject);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setQrPayMode("2");
        model.setTimeExpire(ALIPAY_TIME.format(checkoutExpiresAt(payment)));
        var request = new AlipayTradePagePayRequest();
        request.setBizModel(model);
        request.setNotifyUrl(config.getNotifyUrl());
        request.setReturnUrl(config.getReturnOrigin().replaceAll("/+$", "")
                + "/subscription/payment-result?order=" + payment.orderId()
                + "&kind=" + (recharge ? "recharge" : "package"));
        try {
            return client().pageExecute(request, "GET").getBody();
        } catch (AlipayApiException ex) {
            throw new BizException(1610, "暂时无法打开支付宝收银台，请稍后重试");
        }
    }

    public Trade notification(Map<String, String> fields) {
        String paymentId = fields.get("out_trade_no");
        if (!SIGN_TYPE.equals(fields.get("sign_type"))
                || paymentId == null || !paymentId.matches("ali_[a-f0-9]{32}")
                || config.getAppId().isBlank() || !config.getAppId().equals(fields.get("app_id"))
                || config.getSellerId().isBlank() || !config.getSellerId().equals(fields.get("seller_id"))) {
            throw invalidNotification();
        }
        try {
            var params = new HashMap<>(fields);
            boolean verified = config.isCertificateMode()
                    ? AlipaySignature.rsaCertCheckV1(params, config.getAlipayCertPath(), CHARSET, SIGN_TYPE)
                    : AlipaySignature.rsaCheckV1(params, key(config.getPublicKey()), CHARSET, SIGN_TYPE);
            if (!verified) throw invalidNotification();
            return trade(paymentId, fields.get("trade_status"), fields.get("trade_no"), fields.get("total_amount"));
        } catch (AlipayApiException | IllegalArgumentException | ArithmeticException ex) {
            throw invalidNotification();
        }
    }

    public Trade query(String paymentId) {
        var model = new AlipayTradeQueryModel();
        model.setOutTradeNo(paymentId);
        var request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        var response = execute(request);
        if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
            return new Trade(paymentId, Status.NOT_FOUND, null, null);
        }
        if (!response.isSuccess() || !paymentId.equals(response.getOutTradeNo())) {
            throw new BizException(1610, "暂时无法核对支付宝订单，请稍后重试");
        }
        try {
            return trade(paymentId, response.getTradeStatus(), response.getTradeNo(), response.getTotalAmount());
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new BizException(1610, "支付宝订单状态暂无法核对");
        }
    }

    public boolean close(String paymentId) {
        var model = new AlipayTradeCloseModel();
        model.setOutTradeNo(paymentId);
        var request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        var response = execute(request);
        return response.isSuccess() && paymentId.equals(response.getOutTradeNo());
    }

    private <T extends AlipayResponse> T execute(AlipayRequest<T> request) {
        try {
            return config.isCertificateMode() ? client().certificateExecute(request) : client().execute(request);
        } catch (AlipayApiException ex) {
            throw new BizException(1610, "暂时无法连接支付宝，请稍后重试");
        }
    }

    private synchronized AlipayClient client() throws AlipayApiException {
        if (client != null) return client;
        var gateway = httpsUrl(config.getGateway());
        var origin = httpsUrl(config.getReturnOrigin());
        httpsUrl(config.getNotifyUrl());
        if (!GATEWAYS.contains(gateway.getHost()) || !"/gateway.do".equals(gateway.getPath())
                || origin.getQuery() != null || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))
                || config.getAppId().isBlank() || config.getSellerId().isBlank() || config.getPrivateKey().isBlank()) {
            throw new BizException(1610, "支付宝支付通道尚未配置完成");
        }
        var settings = new AlipayConfig();
        settings.setServerUrl(gateway.toString());
        settings.setAppId(config.getAppId());
        settings.setPrivateKey(key(config.getPrivateKey()));
        settings.setCharset(CHARSET);
        settings.setFormat("json");
        settings.setSignType(SIGN_TYPE);
        settings.setConnectTimeout(3000);
        settings.setReadTimeout(5000);
        if (config.isCertificateMode()) {
            if (config.getAppCertPath().isBlank() || config.getAlipayCertPath().isBlank()
                    || config.getRootCertPath().isBlank()) {
                throw new BizException(1610, "支付宝支付证书尚未配置完成");
            }
            settings.setAppCertPath(config.getAppCertPath());
            settings.setAlipayPublicCertPath(config.getAlipayCertPath());
            settings.setRootCertPath(config.getRootCertPath());
        } else {
            if (config.getPublicKey().isBlank()) throw new BizException(1610, "支付宝公钥尚未配置");
            settings.setAlipayPublicKey(key(config.getPublicKey()));
        }
        client = new DefaultAlipayClient(settings);
        return client;
    }

    private static Trade trade(String paymentId, String rawStatus, String tradeNo, String amount) {
        if (rawStatus == null) throw new IllegalArgumentException("Missing trade status");
        Status status = switch (rawStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> Status.PAID;
            case "WAIT_BUYER_PAY" -> Status.WAITING;
            case "TRADE_CLOSED" -> Status.CLOSED;
            default -> throw new IllegalArgumentException("Unknown trade status");
        };
        if (status != Status.PAID) return new Trade(paymentId, status, tradeNo, null);
        if (tradeNo == null || tradeNo.isBlank() || amount == null) {
            throw new IllegalArgumentException("Missing settlement data");
        }
        return new Trade(paymentId, status, tradeNo, new BigDecimal(amount).movePointRight(2).intValueExact());
    }

    private static URI httpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            if ("https".equals(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null) return uri;
        } catch (IllegalArgumentException ignored) {
            // Configuration errors should not expose secrets or local file paths.
        }
        throw new BizException(1610, "支付宝支付地址尚未配置完成");
    }

    private static String key(String value) {
        return value.replace("\\n", "\n").replaceAll("-----[A-Z ]+-----|\\s+", "");
    }

    private static BizException invalidNotification() {
        return new BizException(1606, "支付宝支付通知未通过校验");
    }
}
