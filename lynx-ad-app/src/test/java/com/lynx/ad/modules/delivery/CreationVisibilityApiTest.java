package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.delivery.api.CreationController;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.Optional;
import org.junit.jupiter.api.*;

class CreationVisibilityApiTest {
    @BeforeEach void setup(){TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(new TenantId("tenant")),Optional.empty(),SysRole.USER));}
    @AfterEach void clear(){TenantContext.clear();}
    @Test void savingSettingReturnsOnlyTheDurableReceiptAndDoesNotRefreshTheWholeMaterialPackage()throws Exception {
        var service=mock(CreationWorkflowService.class);var controller=new CreationController(service,null);
        var request=new CreationWorkflowService.Visibility(7,false);var saved=new CreationState();
        saved.planId="plan";saved.revision=8;saved.packageVersion=4;saved.confirmedVersion=null;saved.hideInAweme=false;
        when(service.visibility(new TenantId("tenant"),"plan",request)).thenReturn(saved);
        var result=controller.visibilitySetting("plan",request).data();
        assertEquals("plan",result.planId());assertEquals(8,result.revision());assertEquals(4,result.packageVersion());assertNull(result.confirmedVersion());assertFalse(result.hideInAweme());
        verify(service).visibility(new TenantId("tenant"),"plan",request);verifyNoMoreInteractions(service);
        assertNotNull(CreationController.class.getMethod("visibilitySetting",String.class,CreationWorkflowService.Visibility.class).getAnnotation(RequiresActiveTrial.class));
    }
}
