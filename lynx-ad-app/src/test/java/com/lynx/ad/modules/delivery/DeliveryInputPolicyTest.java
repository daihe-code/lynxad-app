package com.lynx.ad.modules.delivery;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;
import com.lynx.ad.modules.delivery.application.CreationPayloadBuilder;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryInputPolicyTest {
    @Test void sameBoundaryCasesAsTheBrowserAcrossBothWriteProfiles() throws Exception {
        try(var in=getClass().getResourceAsStream("/delivery-input-cases.json")) {
            for(var row:new ObjectMapper().readTree(in)) {
                var profile="update".equals(row.path("profile").asText())?DeliveryInputPolicy.RULES.update():DeliveryInputPolicy.RULES.creation();
                Runnable validate=()->DeliveryInputPolicy.settings(DeliveryInputPolicy.mode(row.path("mode").asText()),number(row.get("budget")),number(row.get("roi")),profile);
                if(row.path("valid").asBoolean())assertDoesNotThrow(validate::run,row.toString());
                else assertThrows(BizException.class,validate::run,row.toString());
            }
        }
    }
    private BigDecimal number(JsonNode value){return value==null||value.isNull()?null:new BigDecimal(value.asText());}
    @Test void creationAndFinalPublicationUseTheSameVolumeMinimumWithoutInventingAnAmount() {
        var s=new CreationState();s.name="计划";s.smartBidType="CONSERVATIVE";s.budget=new BigDecimal("29.99");
        assertThrows(BizException.class,()->CreationPayloadBuilder.settings(s));
        s.budget=new BigDecimal("30.000");assertDoesNotThrow(()->CreationPayloadBuilder.settings(s));
        assertEquals(new BigDecimal("30.000"),s.budget);
        assertThrows(BizException.class,()->DeliveryInputPolicy.mode("unknown"));
    }
    @Test void titleValidationCatchesShortCopyBeforeStorageOrSync() {
        assertThrows(BizException.class,()->DeliveryInputPolicy.titles(List.of("abc"),true));
        assertDoesNotThrow(()->DeliveryInputPolicy.titles(List.of("  折叠自行车  "),true));
        assertEquals(13,DeliveryInputPolicy.platformLength("折叠自行车123"));
        assertEquals(2,DeliveryInputPolicy.platformLength("𠮷"));
        assertThrows(BizException.class,()->DeliveryInputPolicy.titles(java.util.Collections.nCopies(30,"商品实拍展示"),true));
    }
    @Test void legacyDraftEndpointRejectsMissingOrInvalidBudgetBeforeSavingOrRequestingRecommendations() {
        var plans=org.mockito.Mockito.mock(com.lynx.ad.modules.delivery.domain.port.AdPlanRepository.class);
        var billing=org.mockito.Mockito.mock(com.lynx.ad.modules.billing.application.BillingTrialService.class);
        var service=new com.lynx.ad.modules.delivery.application.PlanCommandService(plans,null,null,null,null,null,billing,null,null,null,new ObjectMapper(),null,null);
        for(var request:List.of(
            new com.lynx.ad.modules.delivery.api.dto.PlanDraftRequest("account","aweme","计划","CUSTOM",null,BigDecimal.ONE,List.of("123"),null),
            new com.lynx.ad.modules.delivery.api.dto.PlanDraftRequest("account","aweme","计划","CUSTOM",new BigDecimal("299.99"),BigDecimal.ONE,List.of("123"),null),
            new com.lynx.ad.modules.delivery.api.dto.PlanDraftRequest("account","aweme","计划","CONSERVATIVE",new BigDecimal("29.99"),null,List.of("123"),null),
            new com.lynx.ad.modules.delivery.api.dto.PlanDraftRequest("account","aweme","计划","unknown",new BigDecimal("300"),null,List.of("123"),null))) {
            assertThrows(BizException.class,()->service.createDraft(new com.lynx.ad.common.domain.Ids.TenantId("tenant"),request));
        }
        org.mockito.Mockito.verify(plans,org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
    @Test void namesTrimAndCalendarDatesAreValidatedBeforeSubmission() {
        assertEquals("计划",DeliveryInputPolicy.name(" 计划 "));
        assertEquals("计划",DeliveryInputPolicy.name("\u00a0计划\ufeff"));
        assertThrows(BizException.class,()->DeliveryInputPolicy.name("\u00a0\ufeff"));
        assertThrows(BizException.class,()->DeliveryInputPolicy.name("中".repeat(31)));
        int year=LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear()+4;
        assertThrows(BizException.class,()->DeliveryInputPolicy.schedule("DATE_RANGE",year+"-02-30",year+"-03-01"));
        assertThrows(BizException.class,()->DeliveryInputPolicy.schedule("DATE_RANGE",year+"-03-02",year+"-03-01"));
    }
}
