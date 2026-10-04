package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.aiclient.ChatCompletionClient;
import com.lynx.ad.aiclient.TtsClient;
import com.lynx.ad.aiclient.VideoGenClient;
import com.lynx.ad.aiclient.VideoJobStatus;
import com.lynx.ad.aiclient.live.LiveChatCompletionClient;
import com.lynx.ad.aiclient.live.LiveVideoGenClient;
import com.lynx.ad.agents.creative.CreativeAgent;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.modules.creative.domain.model.CreativeTask;
import com.lynx.ad.modules.creative.domain.model.TaskStatus;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = LynxAdApplication.class)
@ActiveProfiles("test")
class CreativeAgentLiveIT extends AbstractContainerIT {

    @Autowired
    private CreativeTaskRepository repository;

    @Autowired
    private LynxAdProperties properties;

    @Autowired
    private InboxCommandService inbox;

    @Autowired
    private StoragePort storagePort;

    @Autowired
    private ObjectProvider<com.lynx.ad.modules.creative.domain.port.MaterialUploadPort> uploadPort;

    @Test
    void stubClientsCompleteVideoWithoutUnimplemented() {
        ChatCompletionClient chat = (n, v) -> "title";
        TtsClient tts = text -> new byte[] {0, 0};
        VideoGenClient video = new VideoGenClient() {
            @Override
            public String submit(String payload) {
                return "job-1";
            }

            @Override
            public VideoJobStatus poll(String thirdPartyJobId) {
                return new VideoJobStatus(thirdPartyJobId, "SUCCESS", "");
            }
        };
        CreativeAgent agent = new CreativeAgent(repository, properties, inbox, storagePort, chat, tts, video, uploadPort);
        CreativeTask task = CreativeTask.submitStandard(new TenantId("00000000-0000-0000-0000-000000000001"), Optional.empty(), List.of(new QcProductId("qp01")));
        repository.save(task);
        agent.runNow(task.id());
        CreativeTask saved = repository.findByIdOnly(task.id()).orElseThrow();
        assertEquals(TaskStatus.SUCCEEDED, saved.status());
        assertTrue(saved.currentPackage().isPresent());
        assertNotNull(saved.currentPackage().get().items().get(0).videoObjectKey());
        assertFalseContains(saved.errorMsg());
    }

    @Test
    void emptyAiKeysThrow8001AndNoSuccess() {
        LynxAdProperties empty = new LynxAdProperties();
        LiveChatCompletionClient live = new LiveChatCompletionClient(
                empty, new QianchuanRestClient(new ObjectMapper(), ms -> {}, java.net.http.HttpClient.newHttpClient()), new ObjectMapper());
        BizException ex = assertThrows(BizException.class, () -> live.complete("title", Map.of()));
        assertEquals(8001, ex.code());
        LiveVideoGenClient video = new LiveVideoGenClient(
                empty, new QianchuanRestClient(new ObjectMapper(), ms -> {}, java.net.http.HttpClient.newHttpClient()), new ObjectMapper());
        BizException v = assertThrows(BizException.class, () -> video.submit("{}"));
        assertEquals(8001, v.code());
    }

    private static void assertFalseContains(String error) {
        org.junit.jupiter.api.Assertions.assertTrue(error == null || (!error.contains("未实现") && !error.contains("upstream video failed")));
    }
}
