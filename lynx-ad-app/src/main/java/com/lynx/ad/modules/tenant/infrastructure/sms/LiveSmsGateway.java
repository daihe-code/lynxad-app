package com.lynx.ad.modules.tenant.infrastructure.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.tenant.domain.port.SmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LiveSmsGateway.class);
    private final LynxAdProperties properties;
    private final SmsSender sender;

    public LiveSmsGateway(LynxAdProperties properties) {
        this(properties, request -> {
            var live = properties.getSms().getLive();
            Config config = new Config()
                    .setAccessKeyId(live.getAccessKey())
                    .setAccessKeySecret(live.getAccessSecret());
            config.endpoint = live.getEndpoint();
            return new Client(config).sendSms(request);
        });
    }

    LiveSmsGateway(LynxAdProperties properties, SmsSender sender) {
        this.properties = properties;
        this.sender = sender;
    }

    @Override
    public void send(PhoneNumber phone, String code) {
        if (!properties.getSms().isLiveConfigured()) {
            throw new BizException(1114, "短信服务未配置");
        }
        var live = properties.getSms().getLive();
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone.value())
                .setSignName(live.getSignName())
                .setTemplateCode(live.getTemplateCode())
                .setTemplateParam("{\"code\":\"" + code + "\"}");
        String providerCode = "missing_response";
        try {
            SendSmsResponse response = sender.send(request);
            if (response != null && response.getBody() != null && "OK".equals(response.getBody().getCode())) {
                return;
            }
            providerCode = response == null || response.getBody() == null ? "missing_response" : response.getBody().getCode();
            log.warn("SMS provider rejected send: code={}", providerCode);
        } catch (TeaException e) {
            providerCode = e.getCode();
            log.warn("SMS provider rejected request: code={}", providerCode);
        } catch (Exception e) {
            // Provider exceptions can contain request details. Never log the phone, OTP or credentials.
            log.warn("SMS provider request failed: type={}", e.getClass().getSimpleName());
        }
        throw sendFailure(providerCode);
    }

    private static BizException sendFailure(String providerCode) {
        // Map only documented codes; never expose provider messages or request details.
        // https://help.aliyun.com/zh/sms/developer-reference/api-error-codes
        return switch (providerCode == null ? "" : providerCode) {
            case "isv.MOBILE_NUMBER_ILLEGAL" -> new BizException(1001, "手机号错误，验证码发送失败");
            case "isv.BUSINESS_LIMIT_CONTROL" -> new BizException(1115, "验证码发送过于频繁，请稍后再试");
            case "isv.SMS_SIGNATURE_ILLEGAL", "isv.SIGN_STATE_ILLEGAL",
                 "isv.SMS_SIGNATURE_SCENE_ILLEGAL", "isv.SMS_TEMPLATE_ILLEGAL" ->
                    new BizException(1113, "短信服务配置异常，验证码发送失败");
            case "isv.AMOUNT_NOT_ENOUGH", "isv.OUT_OF_SERVICE" ->
                    new BizException(1113, "短信服务暂不可用，验证码发送失败");
            default -> new BizException(1113, "验证码发送失败，请稍后重试");
        };
    }

    @FunctionalInterface
    interface SmsSender {
        SendSmsResponse send(SendSmsRequest request) throws Exception;
    }
}
