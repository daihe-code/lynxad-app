package com.lynx.ad.modules.delivery.application;

import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanMaterialUsageStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import org.junit.jupiter.api.Test;

class PlanMaterialUsageSyncWorkerTest {
    @Test void readsNativeDetailUsingTenantCredentialWithoutAnyCampaignMutation() throws Exception {
        var store=mock(PlanMaterialUsageStore.class);var credentials=mock(QianchuanCredentialFacade.class);var nativeApi=mock(AdManagementPort.class);
        var job=new PlanMaterialUsageStore.Job("plan","tenant","account","advertiser","101","lease");
        var credential=new Credential("token",new AdvertiserId("advertiser"),"account");
        when(credentials.getUsableCredential(new TenantId("tenant"),new AccountId("account"))).thenReturn(credential);
        var detail=new ObjectMapper().readTree("{\"ad_id\":101,\"multi_product_creative_list\":[]}");when(nativeApi.detail(credential,"101")).thenReturn(detail);
        var worker=new PlanMaterialUsageSyncWorker(store,credentials,nativeApi);
        try {worker.sync(job);verify(nativeApi).detail(credential,"101");verifyNoMoreInteractions(nativeApi);verify(store).success(job,detail);verify(store,never()).failure(any());}
        finally {worker.shutdown();}
    }
    @Test void changedAdvertiserAndProviderFailurePreserveLastSnapshot() {
        var store=mock(PlanMaterialUsageStore.class);var credentials=mock(QianchuanCredentialFacade.class);var nativeApi=mock(AdManagementPort.class);
        var job=new PlanMaterialUsageStore.Job("plan","tenant","account","advertiser","101","lease");
        when(credentials.getUsableCredential(any(),any())).thenReturn(new Credential("token",new AdvertiserId("wrong"),"account"));
        var worker=new PlanMaterialUsageSyncWorker(store,credentials,nativeApi);
        try {worker.sync(job);verifyNoInteractions(nativeApi);verify(store).failure(job);verify(store,never()).success(any(),any());}
        finally {worker.shutdown();}
    }
}
