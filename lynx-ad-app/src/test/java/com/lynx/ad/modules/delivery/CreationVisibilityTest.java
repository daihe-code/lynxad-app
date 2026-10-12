package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class CreationVisibilityTest {
    final CreationStore store=mock(CreationStore.class);
    final ObjectMapper json=new ObjectMapper();
    final CreationState state=new CreationState();
    CreationWorkflowService service;
    @BeforeEach void setup() {
        state.planId="plan";state.tenantId="tenant";state.step=3;
        state.revision=7;state.packageVersion=3;state.confirmedVersion=3;
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{
            assertEquals(state.revision,(Integer)call.getArgument(2));
            Consumer<CreationState> update=call.getArgument(3);update.accept(state);return state;
        });
        service=new CreationWorkflowService(store,null,null,null,null,json,null,null,null,null,
                mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
    }
    @Test void togglesBothWaysAndSurvivesDraftReload() throws Exception {
        var saved=service.visibility(new TenantId("tenant"),"plan",new CreationWorkflowService.Visibility(7,false));
        var reloaded=json.readValue(json.writeValueAsString(saved),CreationState.class);
        assertFalse(CreationView.of(reloaded).hideInAweme());
        assertEquals(8,reloaded.revision);assertEquals(4,reloaded.packageVersion);assertNull(reloaded.confirmedVersion);
        saved=service.visibility(new TenantId("tenant"),"plan",new CreationWorkflowService.Visibility(8,true));
        assertTrue(CreationView.of(saved).hideInAweme());assertEquals(9,saved.revision);
    }
    @Test void selectingCurrentOptionKeepsExistingConfirmation() {
        service.visibility(new TenantId("tenant"),"plan",new CreationWorkflowService.Visibility(7,true));
        assertEquals(7,state.revision);assertEquals(3,state.packageVersion);assertEquals(3,state.confirmedVersion);
    }
    @Test void missingSettingDoesNotSilentlyChooseAlwaysVisible() {
        assertThrows(BizException.class,()->service.visibility(new TenantId("tenant"),"plan",new CreationWorkflowService.Visibility(7,null)));
        verifyNoInteractions(store);
    }
    @Test void publishingAndGeneratingDraftsCannotChangeVisibility() {
        state.publishStatus="CREATED";
        assertThrows(BizException.class,()->service.visibility(new TenantId("tenant"),"plan",new CreationWorkflowService.Visibility(7,false)));
        state.publishStatus="NONE";state.generationStatus="RUNNING";
        assertThrows(BizException.class,()->service.visibility(new TenantId("tenant"),"plan",new CreationWorkflowService.Visibility(7,false)));
        assertTrue(state.hideInAweme);assertEquals(7,state.revision);
    }
}
