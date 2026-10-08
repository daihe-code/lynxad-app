package com.lynx.ad.application.creative;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetReviewWorker;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssetReviewWorkerTest {
    private final AssetUploadRepository repository=mock(AssetUploadRepository.class);
    private final StoragePort storage=mock(StoragePort.class);
    private final AssetReviewPort engine=mock(AssetReviewPort.class);
    private final AssetReviewWorker worker=new AssetReviewWorker(repository,storage,engine);
    @AfterEach void stop() { worker.stop(); }
    private AssetUploadRepository.Review review(String id) { return new AssetUploadRepository.Review("asset","tenant-a","PENDING",id,null,null,List.of(),null,2,Instant.now()); }
    private void file(String kind) {
        var now=Instant.now();
        when(repository.require("tenant-a","asset")).thenReturn(new AssetUploadRepository.Asset("asset","tenant-a","user","request",kind,"file","mime",10,"key","STORED","oss",null,null,"md5",false,null,null,"content",null,null,now,now));
        when(storage.getUrl(new TenantId("tenant-a"),"key")).thenReturn("https://oss.example/signed");
    }
    @Test void onlyApprovalActivatesPlatformSync() {
        file("IMAGE");
        for(String status:List.of("PENDING","REJECTED","MANUAL_REVIEW","FAILED")) {
            when(engine.submit(any(),anyString(),anyString(),anyString(),anyString())).thenReturn(new AssetReviewPort.Result(status,null,null,null,List.of(),null));
            worker.process(review(null));
        }
        verify(repository,never()).activate(anyString(),anyString());
        when(engine.submit(any(),anyString(),anyString(),anyString(),anyString())).thenReturn(new AssetReviewPort.Result("APPROVED","rev_test","approved","ok",List.of(),null));
        worker.process(review(null)); verify(repository).activate("tenant-a","asset");
    }
    @Test void resumedVideoTaskPollsInsteadOfResubmitting() {
        file("VIDEO"); when(engine.poll(AssetKind.VIDEO,"rev_saved")).thenReturn(new AssetReviewPort.Result("PENDING","rev_saved",null,null,List.of(),null));
        var review=review("rev_saved"); worker.process(review);
        verify(engine).poll(AssetKind.VIDEO,"rev_saved"); verify(engine,never()).submit(any(),any(),any(),any(),any());
        verify(repository).finishReview(eq(review),argThat(r->r.reviewId().equals("rev_saved")));
        verify(repository,never()).activate(anyString(),anyString());
    }
    @Test void bothAsyncTypesPersistTaskAndResumePollingWithoutCreatingAnother() {
        for(var kind:List.of(AssetKind.VIDEO,AssetKind.AUDIO)) {
            reset(repository,storage,engine); file(kind.name());
            var pending=new AssetReviewPort.Result("PENDING","rev_saved",null,null,List.of(),null);
            when(engine.submit(eq(kind),anyString(),anyString(),anyString(),anyString())).thenReturn(pending);
            var initial=review(null); worker.process(initial);
            verify(repository).finishReview(initial,pending); verify(engine,never()).poll(any(),any());
            verify(repository,never()).activate(anyString(),anyString());
            when(engine.poll(kind,"rev_saved")).thenReturn(pending);
            worker.process(review("rev_saved"));
            verify(repository,never()).activate(anyString(),anyString());
            var approved=new AssetReviewPort.Result("APPROVED","rev_saved","approved","ok",List.of(),null);
            when(engine.poll(kind,"rev_saved")).thenReturn(approved);
            worker.process(review("rev_saved"));
            verify(engine,times(1)).submit(eq(kind),anyString(),anyString(),anyString(),anyString());
            verify(engine,times(2)).poll(kind,"rev_saved"); verify(repository).activate("tenant-a","asset");
        }
    }
    @Test void outagesRetainFileAndNeverReleaseSync() {
        file("IMAGE"); when(engine.submit(any(),anyString(),anyString(),anyString(),anyString())).thenThrow(new BizException(1525,"引擎不可用"));
        worker.process(review(null)); verify(repository).failReview(any(),eq("引擎不可用")); verify(storage).getUrl(new TenantId("tenant-a"),"key");
        verify(repository,never()).activate(anyString(),anyString()); verifyNoMoreInteractions(storage);
        verify(repository,never()).storageFailed(any(),anyString());
    }
}
