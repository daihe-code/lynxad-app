package com.lynx.ad.modules.billing.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.domain.model.PayChannel;
import com.lynx.ad.modules.billing.domain.port.PaymentPort;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WechatPaymentAdapter implements PaymentPort {

    private final LynxAdProperties properties;
    private RSAAutoCertificateConfig config;

    public WechatPaymentAdapter(LynxAdProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentCreateResult create(PaymentCreateCommand command) {
        var wx = properties.getPayment().getWechat();
        if (!properties.getPayment().isLiveConfigured() || wx.getNotifyUrl().isBlank() || wx.getSerialNo().isBlank()) {
            throw new BizException(1610, "支付通道未配置");
        }
        try {
            var request = new PrepayRequest();
            var amount = new Amount();
            amount.setTotal(command.amountFen());
            amount.setCurrency("CNY");
            request.setAmount(amount);
            request.setMchid(wx.getMchId());
            request.setAppid(wx.getAppId());
            request.setDescription(command.description());
            request.setOutTradeNo(command.paymentId());
            request.setNotifyUrl(command.notifyUrl());
            var response = new NativePayService.Builder().config(config()).build().prepay(request);
            return new PaymentCreateResult(PayChannel.WECHAT,null,null,response.getCodeUrl());
        } catch (RuntimeException e) {
            throw new BizException(1610,"暂时无法创建微信支付，请稍后重试");
        }
    }

    @Override
    public PaymentNotify parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        if (!properties.getPayment().isLiveConfigured()) {
            throw new BizException(1606, "回调验签失败");
        }
        try {
            var wx = properties.getPayment().getWechat();
            var request = new RequestParam.Builder()
                    .serialNumber(header(headers,"Wechatpay-Serial")).nonce(header(headers,"Wechatpay-Nonce"))
                    .signature(header(headers,"Wechatpay-Signature")).timestamp(header(headers,"Wechatpay-Timestamp"))
                    .body(rawBody).build();
            var tx = new NotificationParser(config()).parse(request,Transaction.class);
            if (!wx.getMchId().equals(tx.getMchid()) || !wx.getAppId().equals(tx.getAppid()) || tx.getAmount() == null
                    || !"CNY".equals(tx.getAmount().getCurrency()) || tx.getOutTradeNo() == null || tx.getTransactionId() == null)
                throw new IllegalArgumentException();
            return new PaymentNotify(tx.getOutTradeNo(),Transaction.TradeStateEnum.SUCCESS.equals(tx.getTradeState()),
                    tx.getTransactionId(),tx.getAmount().getTotal());
        } catch (RuntimeException e) {
            throw new BizException(1606,"支付回调校验失败");
        }
    }

    private synchronized RSAAutoCertificateConfig config() {
        if (config == null) {
            var wx = properties.getPayment().getWechat();
            config = new RSAAutoCertificateConfig.Builder().merchantId(wx.getMchId()).merchantSerialNumber(wx.getSerialNo())
                    .privateKey(wx.getPrivateKeyPem()).apiV3Key(wx.getApiV3Key()).build();
        }
        return config;
    }

    private static String header(Map<String,String> headers,String name) {
        return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue)
                .findFirst().orElseThrow();
    }

    @Override
    public boolean verifyMockComplete(String paymentId, String mockNonce) {
        return false;
    }

    public PayChannel channel() {
        return PayChannel.WECHAT;
    }
}
