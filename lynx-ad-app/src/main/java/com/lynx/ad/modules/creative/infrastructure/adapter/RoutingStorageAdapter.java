package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.port.*;
import java.io.InputStream;
import java.util.List;

public class RoutingStorageAdapter implements RoutedStoragePort, AutoCloseable {
    private final StoragePort local, oss, defaults, engine;
    public RoutingStorageAdapter(String defaultMode, StoragePort local, StoragePort oss) {
        this(defaultMode,local,oss,null);
    }
    public RoutingStorageAdapter(String defaultMode, StoragePort local, StoragePort oss, StoragePort engine) {
        this.engine=engine;
        this.local=local; this.oss=oss; this.defaults=forMode(defaultMode);
    }
    @Override public StoragePort forMode(String mode) {
        if ("local".equalsIgnoreCase(mode)) return local;
        if ("oss".equalsIgnoreCase(mode)) return oss;
        if (EngineAssetStorage.MODE.equalsIgnoreCase(mode)&&engine!=null) return engine;
        throw new BizException(1507,"存储类型不支持，请检查平台存储配置");
    }
    @Override public StorageObject put(TenantId tenant,String key,InputStream in,String type) { return defaults.put(tenant,key,in,type); }
    @Override public StorageObject put(TenantId tenant,String key,InputStream in,long size,String type) { return defaults.put(tenant,key,in,size,type); }
    @Override public String getUrl(TenantId tenant,String key) { return defaults.getUrl(tenant,key); }
    @Override public void delete(TenantId tenant,String key) { defaults.delete(tenant,key); }
    @Override public byte[] read(TenantId tenant,String key) { return defaults.read(tenant,key); }
    @Override public InputStream open(TenantId tenant,String key) { return defaults.open(tenant,key); }
    @Override public UploadTicket beginUpload(TenantId tenant,String key,long size,String type) { return defaults.beginUpload(tenant,key,size,type); }
    @Override public void completeUpload(TenantId tenant,String key,String id,List<UploadPart> parts,long size) { defaults.completeUpload(tenant,key,id,parts,size); }
    @Override public void abortUpload(TenantId tenant,String key,String id) { defaults.abortUpload(tenant,key,id); }
    @Override public void close() throws Exception { if(oss instanceof AutoCloseable closeable) closeable.close(); }
}
