package com.lynx.ad.application.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.TenantAuthSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotifyContactIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void submitPhoneCreatesRowAndIgnoresMessageBody() throws Exception {
        JsonNode ok = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.post(mvc, "/api/v1/notify/contact", "{\"phone\":\"13900002005\"}", null));
        assertEquals(0, ok.path("code").asInt());
        String id = ok.path("data").path("ticketId").asText();
        assertTrue(id.length() > 8);
        Integer rows = jdbc.queryForObject("select count(*) from contact_ticket where id = ?", Integer.class, id);
        assertEquals(1, rows);

        JsonNode extra = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.post(
                        mvc,
                        "/api/v1/notify/contact",
                        "{\"phone\":\"13900002006\",\"message\":\"should-ignore\"}",
                        null));
        assertEquals(0, extra.path("code").asInt());
        Integer cols = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'contact_ticket' and column_name in ('message','body')",
                Integer.class);
        assertEquals(0, cols);
    }
}
