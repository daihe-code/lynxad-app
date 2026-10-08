package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.domain.service.AssetFilePolicy;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.nio.file.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Compatibility for generated videos; multipart implementation is shared with local file uploads. */
@Component @Primary
@ConditionalOnProperty(prefix="lynx.ad.mock",name="qianchuan",havingValue="false")
public class LiveMaterialUploadAdapter implements MaterialUploadPort {
    private final StoragePort storage;
    private final AssetUploadPort upload;
    public LiveMaterialUploadAdapter(StoragePort storage,AssetUploadPort upload) { this.storage=storage; this.upload=upload; }
    @Override public String uploadVideo(TenantId tenant,Credential credential,String key,String filename) {
        Path temp=null;
        try {
            temp=Files.createTempFile("lynx-generated-upload-",".mp4");
            try(var in=storage.open(tenant,key)) { Files.copy(in,temp,StandardCopyOption.REPLACE_EXISTING); }
            var checked=AssetFilePolicy.validate(temp,filename,Files.size(temp));
            return upload.upload(credential,AssetKind.VIDEO,temp,filename,"video/mp4",checked.md5(),true).objectId();
        } catch(BizException ex) { throw ex; }
        catch(Exception ex) { throw new BizException(1511,"视频素材读取失败"); }
        finally { AssetUploadService.remove(temp); }
    }
}
