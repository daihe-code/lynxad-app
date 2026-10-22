package com.lynx.ad.modules.delivery;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.delivery.api.CreationController;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductCatalogApiTest {
    final TenantId tenant=new TenantId("tenant");
    @BeforeEach void context(){TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));}
    @AfterEach void clear(){TenantContext.clear();}

    @Test void cursorMetadataAndLargeIntegersSurviveDraftDecorationWithoutSavingAnything()throws Exception {
        var workflow=mock(CreationWorkflowService.class);var qc=mock(CreationQianchuanGateway.class);var drafts=mock(DraftProductStore.class);
        var controller=new CreationController(workflow,qc);controller.draftProducts(drafts);
        when(qc.products(tenant,"account","aweme",2,10,null,"NEW_PRODUCT",false,null,false,"9007199254740993"))
            .thenReturn(new CreationQianchuanGateway.Page(List.of(),0,2,10,"9007199254740995",true));
        when(drafts.decorate("tenant","account","draft",List.of())).thenReturn(List.of());
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/delivery/creation/products").param("accountId","account").param("awemeId","aweme")
                .param("tab","NEW_PRODUCT").param("page","2").param("cursor","9007199254740993").param("draftId","draft"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextCursor").isString())
            .andExpect(jsonPath("$.data.nextCursor").value("9007199254740995"))
            .andExpect(jsonPath("$.data.hasMore").value(true)).andExpect(jsonPath("$.data.page").value(2));
        verify(qc).products(tenant,"account","aweme",2,10,null,"NEW_PRODUCT",false,null,false,"9007199254740993");
        verify(drafts).decorate("tenant","account","draft",List.of());verifyNoInteractions(workflow);
    }
    @Test void existingNumberedRequestsStillWorkWithoutACursor()throws Exception {
        var workflow=mock(CreationWorkflowService.class);var qc=mock(CreationQianchuanGateway.class);
        when(qc.products(tenant,"account","aweme",1,10,null,"ALL",false,null,false,null))
            .thenReturn(new CreationQianchuanGateway.Page(List.of(),39,1,10));
        MockMvcBuilders.standaloneSetup(new CreationController(workflow,qc)).build()
            .perform(get("/api/v1/delivery/creation/products").param("accountId","account").param("awemeId","aweme"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(39)).andExpect(jsonPath("$.data.hasMore").value(false));
        verifyNoInteractions(workflow);
    }
}
