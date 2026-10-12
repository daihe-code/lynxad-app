package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.service.AssetStoragePolicy;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Opt-in backfill: verify copied objects before changing addresses; retain old objects. */
@Component
@ConditionalOnProperty(prefix="lynx.ad.storage",name="migrate-legacy",havingValue="true")
public class AssetStorageMigration implements ApplicationRunner {
    private final LynxAdProperties properties;
    private final AssetUploadRepository repository;
    private final JdbcTemplate jdbc;
    public AssetStorageMigration(LynxAdProperties properties,AssetUploadRepository repository,JdbcTemplate jdbc){this.properties=properties;this.repository=repository;this.jdbc=jdbc;}
    @Override public void run(ApplicationArguments args) throws Exception {
        var config=properties.getStorage().getOss();
        try(var client=new AliyunOssClient(config)) {
            var rows=jdbc.queryForList("SELECT id,tenant_id FROM creative_asset WHERE storage_mode='oss' AND canonical_id IS NULL AND origin='LOCAL_UPLOAD'");
            for(var row:rows) {
                String tenant=(String)row.get("tenant_id"),id=(String)row.get("id");var asset=repository.require(tenant,id);
                if("TEXT".equals(asset.kind())) {
                    jdbc.update("UPDATE creative_asset SET object_key=NULL,bucket=NULL,storage_endpoint=NULL,path_version='DB' WHERE tenant_id=? AND id=?",tenant,id);
                    continue;
                }
                var meta=repository.storageMetadata(tenant,id);
                String sourceBucket=meta.bucket()==null?config.getBucket():meta.bucket();
                if(sourceBucket==null||sourceBucket.isBlank())throw new IllegalStateException("旧素材桶配置缺失，停止迁移");
                if(!"STORED".equals(asset.storageStatus())) {
                    if(meta.bucket()==null)repository.storageMetadata(tenant,id,sourceBucket,config.getEndpoint(),"LEGACY","LOCAL_UPLOAD");
                    continue;
                }
                String targetBucket=AssetStoragePolicy.bucket(config,asset.kind());
                var source=client.metadata(sourceBucket,asset.objectKey());
                if(source.size()!=asset.sizeBytes())throw new IllegalStateException("旧素材大小与数据库不一致，停止迁移");
                if(targetBucket.equals(sourceBucket)) {
                    repository.storageMetadata(tenant,id,sourceBucket,config.getEndpoint(),meta.pathVersion(),"LOCAL_UPLOAD");
                    repository.etag(tenant,id,source.etag());continue;
                }
                String key=AssetStoragePolicy.key(new TenantId(tenant),id,asset.filename());
                client.copyObject(sourceBucket,asset.objectKey(),targetBucket,key);
                var target=client.metadata(targetBucket,key);
                if(target.size()!=asset.sizeBytes())throw new IllegalStateException("复制后文件大小不一致，原地址已保留");
                if(asset.md5()!=null) {
                    var md5=MessageDigest.getInstance("MD5");
                    try(var in=client.openObject(targetBucket,key)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)md5.update(buffer,0,n);}
                    if(!asset.md5().equalsIgnoreCase(HexFormat.of().formatHex(md5.digest())))throw new IllegalStateException("复制后校验不一致，原地址已保留");
                }
                int changed=jdbc.update("UPDATE creative_asset SET bucket=?,object_key=?,storage_endpoint=?,path_version='V2',etag=? WHERE tenant_id=? AND id=? AND object_key=? AND storage_status='STORED'",targetBucket,key,config.getEndpoint(),target.etag(),tenant,id,asset.objectKey());
                if(changed!=1)throw new IllegalStateException("素材记录已变化，停止切换存储地址");
            }
        }
    }
}
