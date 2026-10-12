package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.infrastructure.adapter.EngineAssetStorage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CreationAssetImporter {
    private final AssetUploadService assets;
    private final EngineAssetStorage engine;
    public CreationAssetImporter(AssetUploadService assets,EngineAssetStorage engine){this.assets=assets;this.engine=engine;}

    /** Register engine-owned OSS files and text without downloading, copying or reviewing them. */
    public String reference(TenantId tenant,UserId user,String key,String job,String material,String component,String kind,String account,boolean composite) {
        String request=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        var asset=engine.register(tenant,user,request,job,material,component,kind,composite);
        if(account!=null)assets.ensureTarget(tenant,asset.id(),account);
        return asset.id();
    }

    /** User-entered titles still follow the uploaded text path, rather than gaining engine provenance. */
    public String ingest(TenantId tenant,UserId user,String key,String value,String kind,String account,boolean component) {
        if(!"TEXT".equals(kind))throw new BizException(1521,"生成文件请直接登记引擎 OSS 引用");
        Path temp=null;
        try {
            temp=Files.createTempFile("lynx-title-", ".txt");Files.writeString(temp,value,StandardCharsets.UTF_8);
            String request=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
            return assets.ingest(tenant,user,request,temp,"titles.txt",false,account,component).id();
        }catch(BizException e){throw e;}catch(Exception e){throw new BizException(1521,"文案保存失败");}
        finally{AssetUploadService.remove(temp);}
    }
}
