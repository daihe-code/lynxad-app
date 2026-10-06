package com.lynx.ad.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public final class CreativeTaskSupport {
    private CreativeTaskSupport() {}

    public static JsonNode awaitSucceeded(MockMvc mvc, ObjectMapper mapper, String taskId, String token) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var response = mvc.perform(MockMvcRequestBuilders.get("/api/v1/creative/creatives/tasks/" + taskId)
                    .header("Authorization", "Bearer " + token)).andReturn().getResponse();
            var body = mapper.readTree(response.getContentAsString());
            if (response.getStatus() != 200 || body.path("code").asInt(-1) != 0) {
                throw new AssertionError("Creative task query failed");
            }
            var task = body.path("data");
            var status = task.path("status").asText();
            if ("SUCCEEDED".equals(status)) return task;
            if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                throw new AssertionError("Creative task stopped: " + status);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for creative task completion");
    }
}
