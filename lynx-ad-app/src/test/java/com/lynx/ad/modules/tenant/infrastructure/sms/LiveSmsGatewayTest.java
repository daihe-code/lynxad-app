package com.lynx.ad.modules.tenant.infrastructure.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LiveSmsGatewayTest {

    private static final PhoneNumber PHONE = new PhoneNumber("13900002003");

    @Test
    void sendsApprovedTemplateAndSixDigitCode() {
        var properties = configured();
        var calls = new AtomicInteger();
        var gateway = new LiveSmsGateway(properties, request -> {
            calls.incrementAndGet();
            assertEquals(PHONE.value(), request.getPhoneNumbers());
            assertEquals("灵投", request.getSignName());
            assertEquals("SMS_338901310", request.getTemplateCode());
            assertEquals("{\"code\":\"123456\"}", request.getTemplateParam());
            return response("OK");
        });

        gateway.send(PHONE, "123456");
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsProviderFailureWithoutExposingProviderDetails() {
        var gateway = new LiveSmsGateway(configured(), request -> response("isv.SMS_SIGNATURE_ILLEGAL"));
        BizException error = assertThrows(BizException.class, () -> gateway.send(PHONE, "123456"));
        assertEquals(1113, error.code());
        assertEquals("短信发送失败", error.getMessage());
    }

    @Test
    void rejectsTransportFailureWithoutSavingAnOtp() {
        var gateway = new LiveSmsGateway(configured(), request -> {
            throw new IllegalStateException("provider request contains sensitive values");
        });
        BizException error = assertThrows(BizException.class, () -> gateway.send(PHONE, "123456"));
        assertEquals(1113, error.code());
    }

    @Test
    void refusesPlaceholderSignOrUnknownVendor() {
        var properties = configured();
        properties.getSms().getLive().setSignName("EMPTY");
        var gateway = new LiveSmsGateway(properties, request -> {
            throw new AssertionError("should not call provider");
        });
        assertEquals(1114, assertThrows(BizException.class, () -> gateway.send(PHONE, "123456")).code());

        properties.getSms().getLive().setSignName("灵投");
        properties.getSms().getLive().setVendor("unknown");
        assertEquals(1114, assertThrows(BizException.class, () -> gateway.send(PHONE, "123456")).code());
    }

    private static LynxAdProperties configured() {
        var properties = new LynxAdProperties();
        var live = properties.getSms().getLive();
        live.setVendor("aliyun");
        live.setEndpoint("dysmsapi.aliyuncs.com");
        live.setAccessKey("test-key");
        live.setAccessSecret("test-secret");
        live.setSignName("灵投");
        live.setTemplateCode("SMS_338901310");
        return properties;
    }

    private static SendSmsResponse response(String code) {
        return new SendSmsResponse().setBody(new SendSmsResponseBody().setCode(code));
    }
}
