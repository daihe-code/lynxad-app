package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class CreationCarouselVersionTest {
    @Test void refreshedPicturesCreateNewBundleWhileRetryingSameComponentsReusesIt() {
        var store=mock(CreationStore.class);
        var bundles=mock(CarouselService.class);
        var assets=mock(AssetUploadService.class);
        var payload=mock(CreationPayloadBuilder.class);
        var state=new CreationState();
        state.tenantId="tenant";state.userId="user";state.planId="plan";state.accountId="account";
        state.generationStatus="SUCCEEDED";
        var material=new CreationState.Material();material.id="same-engine-material";material.type="CAROUSEL";
        material.title="骑行手套";material.textAssetId="text";material.audioAssetId="audio";
        material.imageAssetIds=new ArrayList<>(List.of("image-1","image-2"));state.materials.add(material);
        when(store.get("tenant","plan")).thenReturn(state);
        when(store.mutate(eq("tenant"),eq("plan"),isNull(),any())).thenAnswer(call->{
            Consumer<CreationState> update=call.getArgument(3);update.accept(state);return state;
        });
        when(assets.detail(any(),anyString())).thenAnswer(call->CreationPayloadTest.asset(call.getArgument(1),"IMAGE","NOT_REQUIRED",null,null,null));
        var accepted=new LinkedHashMap<String,CarouselService.Create>();
        when(bundles.create(any(),any(),any())).thenAnswer(call->{
            CarouselService.Create request=call.getArgument(2);
            var previous=accepted.putIfAbsent(request.requestId(),request);
            if(previous!=null&&!previous.equals(request))throw new BizException(1520,"图文请求标识已用于其他内容");
            return new CarouselService.Detail(request.requestId(),request.name(),"now",List.of(),null,request.accountIds());
        });
        var service=new CreationWorkflowService(store,null,null,null,null,new ObjectMapper(),null,assets,bundles,payload,null,mock(PlatformTransactionManager.class));
        service.process(state);String original=material.carouselId;
        assertEquals("READY",material.status);

        material.carouselId=null;material.status="PROCESSING";service.process(state);
        assertEquals(original,material.carouselId);assertEquals(1,accepted.size());

        material.imageAssetIds=new ArrayList<>(List.of("new-image-1","new-image-2"));
        material.carouselId=null;material.status="PROCESSING";service.process(state);
        String replacement=material.carouselId;
        assertEquals("READY",material.status);assertNull(material.error);assertNotEquals(original,replacement);
        assertEquals(List.of("new-image-1","new-image-2"),accepted.get(replacement).imageAssetIds());
        assertEquals(2,accepted.size());

        material.carouselId=null;material.status="PROCESSING";service.process(state);
        assertEquals(replacement,material.carouselId);assertEquals(2,accepted.size());

        Collections.reverse(material.imageAssetIds);material.carouselId=null;material.status="PROCESSING";
        service.process(state);assertNotEquals(replacement,material.carouselId);assertEquals(3,accepted.size());
        String reordered=material.carouselId;
        material.audioAssetId="new-audio";material.carouselId=null;material.status="PROCESSING";
        service.process(state);assertNotEquals(reordered,material.carouselId);assertEquals(4,accepted.size());
        assertEquals("READY",material.status);
    }
}
