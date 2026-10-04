package com.lynx.ad.application.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public final class BillingFlowHelper {

    private BillingFlowHelper() {}

    public static JsonNode subscribeAndPay(MockMvc mvc, ObjectMapper mapper, String token, String planCode) throws Exception {
        JsonNode order = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"" + planCode + "\"}"));
        if (order.path("code").asInt() != 0) {
            return order;
        }
        if ("DOWNGRADE".equals(order.path("data").path("type").asText())
                || order.path("data").path("payableFen").asInt() == 0) {
            return order;
        }
        String orderId = order.path("data").path("orderId").asText();
        JsonNode pay = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\"}"));
        return BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments/"
                                + pay.path("data").path("paymentId").asText()
                                + "/mock-complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true,\"nonce\":\""
                                + pay.path("data").path("mockNonce").asText()
                                + "\"}"));
    }
}
