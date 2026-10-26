package com.lynx.ad.modules.tenant.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.tenant.domain.model.InboxResult;

final class InboxResultJson {
    private static final ObjectMapper JSON = new ObjectMapper();
    static String write(InboxResult result) {
        if (result == null) return null;
        try { return JSON.writeValueAsString(result); }
        catch (JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid inbox result", invalid); }
    }
    static InboxResult read(String value) {
        if (value == null || value.isBlank()) return null;
        try { return JSON.readValue(value, InboxResult.class); }
        catch (JsonProcessingException invalid) { return null; }
    }
    private InboxResultJson() {}
}
