package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.delivery.api.CreationController;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.creative.api.AssetUploadController;
import com.lynx.ad.modules.creative.application.*;
import org.junit.jupiter.api.*;
import java.util.Optional;

class AssetReconciliationApiTest {
    final TenantId tenant=new TenantId("tenant");
    @BeforeEach void setup(){TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));}
    @AfterEach void clear(){TenantContext.clear();}
    @Test void creationEndpointReturnsReadOnlyResultDirectlyAndNeverCallsMutatingGetProjection()throws Exception {
        var workflow=mock(CreationWorkflowService.class);var controller=new CreationController(workflow,null);var version=new CreationWorkflowService.Version(4);
        controller.verifySync("plan","material",version);verify(workflow).verifyMaterialSync(tenant,"plan","material",version);verifyNoMoreInteractions(workflow);
        assertNull(CreationController.class.getMethod("verifySync",String.class,String.class,CreationWorkflowService.Version.class).getAnnotation(RequiresActiveTrial.class));
    }
    @Test void assetEndpointDoesNotQueueUploadsAndDoesNotRequireAWriteTrial()throws Exception {
        var files=mock(AssetUploadService.class);var verifier=mock(AssetSyncReconciliationService.class);var controller=new AssetUploadController(files,null);controller.reconciliation(verifier);
        controller.reconcile("asset",new AssetUploadController.ReconcileRequest("account"));verify(verifier).reconcile(tenant,"asset","account");verifyNoInteractions(files);
        assertNull(AssetUploadController.class.getMethod("reconcile",String.class,AssetUploadController.ReconcileRequest.class).getAnnotation(RequiresActiveTrial.class));
    }
}
