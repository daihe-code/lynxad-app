package com.lynx.ad.modules.delivery.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/** Non-creation management operations. Authorization lifecycle remains in identity. */
public interface AdManagementPort {
    JsonNode detail(Credential credential, String adId);
    Map<String, String> guarantees(Credential credential, List<String> adIds);
    void settings(Credential credential, String adId, String name, java.math.BigDecimal budget, java.math.BigDecimal roi);
    List<JsonNode> report(Credential credential, LocalDate date);
    List<JsonNode> materials(Credential credential, String adId);
    String control(Credential credential, String adId, String scene, String name,
                   java.math.BigDecimal budget, java.math.BigDecimal duration, List<String> materialIds);
    List<JsonNode> controls(Credential credential, String adId);
}
