package com.lynx.ad.modules.delivery;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.delivery.api.CreationController;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductSuggestionApiTest {
    @Test void clickedProductAndFullSelectionAreForwardedSeparately()throws Exception {
        var tenant=new TenantId("tenant");TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        var suggestions=mock(ProductRecommendationService.class);var controller=new CreationController(mock(CreationWorkflowService.class),mock(CreationQianchuanGateway.class));controller.productSuggestions(suggestions);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/delivery/creation/products/suggestions").param("accountId","account").param("awemeId","aweme")
                .param("productIds","9007199254740995").param("excludeProductIds","9007199254740995,100,200"))
                .andExpect(status().isOk());
        verify(suggestions).recommend(tenant,"account","aweme",List.of("9007199254740995"),List.of("9007199254740995","100","200"),null,false);
    }
    @AfterEach void clear(){TenantContext.clear();}
    @Test void browserRequestKeepsLargeIdsAsStringsAndUsesCurrentTenantWithoutSavingDraft()throws Exception {
        var tenant=new TenantId("tenant");TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        var workflow=mock(CreationWorkflowService.class);var qc=mock(CreationQianchuanGateway.class);var suggestions=mock(ProductRecommendationService.class);
        var controller=new CreationController(workflow,qc);controller.productSuggestions(suggestions);
        when(suggestions.recommend(tenant,"account","aweme",List.of("9007199254740995","100"),"draft",true)).thenReturn(
                new ProductRecommendationService.Result("account","aweme",List.of("9007199254740995","100"),List.of(),20,20,false,false,"2026-10-21T00:00:00Z","PRODUCT_FACTS_V1","商品信息匹配"));
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/delivery/creation/products/suggestions").param("accountId","account").param("awemeId","aweme")
                .param("productIds","9007199254740995,100").param("draftId","draft").param("refresh","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceProductIds[0]").value("9007199254740995"));
        verify(suggestions).recommend(tenant,"account","aweme",List.of("9007199254740995","100"),"draft",true);verifyNoInteractions(workflow,qc);
    }
}
