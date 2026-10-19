package com.lynx.ad.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.catalog.domain.port.ProductPoolPort;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.LiveFixtureServer;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductPoolPortIT extends AbstractContainerIT {

    @Autowired
    private ProductPoolPort productPoolPort;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void mockPoolStillQp01ToQp08() throws Exception {
        onboard("13900008051", "选品店");
        Set<String> ids = productPoolPort.list(new AccountId("local"), "aweme").stream()
                .map(p -> p.id().value())
                .collect(Collectors.toSet());
        assertEquals(Set.of("qp01", "qp02", "qp03", "qp04", "qp05", "qp06", "qp07", "qp08"), ids);
    }

    @Test
    void livePoolUsesFixtureNotBakeryAndFailIs1306() throws Exception {
        LiveFixtureServer fixture = new LiveFixtureServer();
        try {
            var props = new com.lynx.ad.common.config.LynxAdProperties();
            props.getQianchuan().getCatalog().setProductGetUrl(fixture.base() + "/catalog/product");
            var rest = new com.lynx.ad.common.http.QianchuanRestClient(mapper, ms -> {}, java.net.http.HttpClient.newHttpClient());
            var ocean = new com.lynx.ad.common.http.OceanJson(mapper);
            var accounts = org.mockito.Mockito.mock(com.lynx.ad.modules.identity.domain.port.OauthAccountRepository.class);
            var account = com.lynx.ad.modules.identity.domain.model.OauthAccount.authorize(
                    new com.lynx.ad.common.domain.Ids.TenantId("t"),
                    new com.lynx.ad.common.domain.Ids.AdvertiserId("adv"),
                    "n",
                    new com.lynx.ad.modules.identity.domain.model.EncryptedToken("a"),
                    new com.lynx.ad.modules.identity.domain.model.EncryptedToken("r"),
                    java.time.Instant.now().plusSeconds(60),
                    java.time.Instant.now().plusSeconds(60),
                    java.util.List.of());
            org.mockito.Mockito.when(accounts.findByIdOnly(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(account));
            var encryptor = org.mockito.Mockito.mock(com.lynx.ad.common.crypto.Encryptor.class);
            org.mockito.Mockito.when(encryptor.decrypt(org.mockito.ArgumentMatchers.any())).thenReturn("tok");
            var facade = new com.lynx.ad.modules.identity.application.QianchuanCredentialFacade(accounts, encryptor, props);
            var live = new com.lynx.ad.modules.catalog.infrastructure.adapter.LiveProductPoolAdapter(rest, props, ocean, facade);
            var items = live.list(account.id(), "aweme");
            assertTrue(items.stream().noneMatch(p -> p.id().value().startsWith("qp0")));
            assertTrue(items.stream().anyMatch(p -> "live_p01".equals(p.id().value())));
            fixture.productFail = true;
            BizException ex = assertThrows(BizException.class, () -> live.list(account.id(), "aweme"));
            assertEquals(1306, ex.code());
        } finally {
            fixture.productFail = false;
            fixture.stop();
        }
    }

    @Test
    void livePoolReadsProductNameAndImg() throws Exception {
        LiveFixtureServer fixture = new LiveFixtureServer();
        fixture.productAltFields = true;
        try {
            var props = new com.lynx.ad.common.config.LynxAdProperties();
            props.getQianchuan().getCatalog().setProductGetUrl(fixture.base() + "/catalog/product");
            var rest = new com.lynx.ad.common.http.QianchuanRestClient(
                    mapper, ms -> {}, java.net.http.HttpClient.newHttpClient());
            var ocean = new com.lynx.ad.common.http.OceanJson(mapper);
            var accounts = org.mockito.Mockito.mock(com.lynx.ad.modules.identity.domain.port.OauthAccountRepository.class);
            var account = com.lynx.ad.modules.identity.domain.model.OauthAccount.authorize(
                    new com.lynx.ad.common.domain.Ids.TenantId("t"),
                    new com.lynx.ad.common.domain.Ids.AdvertiserId("adv"),
                    "n",
                    new com.lynx.ad.modules.identity.domain.model.EncryptedToken("a"),
                    new com.lynx.ad.modules.identity.domain.model.EncryptedToken("r"),
                    java.time.Instant.now().plusSeconds(60),
                    java.time.Instant.now().plusSeconds(60),
                    java.util.List.of());
            org.mockito.Mockito.when(accounts.findByIdOnly(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(java.util.Optional.of(account));
            var encryptor = org.mockito.Mockito.mock(com.lynx.ad.common.crypto.Encryptor.class);
            org.mockito.Mockito.when(encryptor.decrypt(org.mockito.ArgumentMatchers.any())).thenReturn("tok");
            var facade = new com.lynx.ad.modules.identity.application.QianchuanCredentialFacade(accounts, encryptor, props);
            var live = new com.lynx.ad.modules.catalog.infrastructure.adapter.LiveProductPoolAdapter(
                    rest, props, ocean, facade);
            var items = live.list(account.id(), "aweme");
            assertEquals(1, items.size());
            assertEquals("live_p02", items.get(0).id().value());
            assertEquals("回退面包", items.get(0).name());
            assertEquals("http://img.example/p.png", items.get(0).imageUrl());
            assertEquals(0, items.get(0).price().amount().compareTo(new java.math.BigDecimal("19.9")));
        } finally {
            fixture.productAltFields = false;
            fixture.stop();
        }
    }

    private void onboard(String phone, String company) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andReturn();
        MvcResult login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"123456\"}"))
                .andReturn();
        String token = mapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"测\",\"companyName\":\""
                                + company
                                + "\",\"password\":\"Passw0rd!\",\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andReturn();
        JsonNode unused = mapper.createObjectNode();
        assertFalse(unused.isNull());
    }
}
