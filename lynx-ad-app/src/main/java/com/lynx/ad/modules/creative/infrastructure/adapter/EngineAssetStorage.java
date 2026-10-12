package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** Read-only references to engine-owned OSS objects. No file copy and no upload review. */
@Component
public class EngineAssetStorage implements StoragePort {
    public static final String MODE="engine_oss";
    private final AssetUploadRepository repository;
    private final CreationEngineClient engine;
    private StoragePort objects;
    private com.lynx.ad.common.config.LynxAdProperties properties;
    public void useObjectStorage(StoragePort objects,com.lynx.ad.common.config.LynxAdProperties properties){this.objects=objects;this.properties=properties;}
    private String assetId(String tenant,String key){return repository.storageObject(tenant,key).map(Asset::id).orElse(key);}
    private boolean located(String tenant,String key){var meta=repository.storageMetadata(tenant,assetId(tenant,key));return objects!=null&&meta!=null&&meta.bucket()!=null&&!meta.bucket().isBlank();}
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private record Cached(Instant until,JsonNode job) {}
    private final Map<String,Cached> cache=new java.util.concurrent.ConcurrentHashMap<>();
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"engine-oss-read-deadline");t.setDaemon(true);return t;});
    public EngineAssetStorage(AssetUploadRepository repository,CreationEngineClient engine){this.repository=repository;this.engine=engine;}

    private JsonNode material(String tenant,String job,String mid,boolean fresh) {
        String key=tenant+":"+job;var cached=cache.get(key);
        JsonNode result;
        if(!fresh&&cached!=null&&cached.until().isAfter(Instant.now()))result=cached.job();
        else {
            result=engine.get("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(job)+"?store_id="+CreationEngineClient.query(tenant));
            if(!job.equals(result.path("job_id").asText()))throw new BizException(1521,"引擎任务响应不匹配");
            cache.entrySet().removeIf(e->e.getValue().until().isBefore(Instant.now()));
            cache.put(key,new Cached(Instant.now().plusSeconds(30),result));
        }
        for(var group:result.path("groups"))for(var item:group.path("materials"))
            if(mid.equals(item.path("material_id").asText())&&"succeeded".equals(item.path("status").asText()))return item;
        if(!fresh&&cached!=null)return material(tenant,job,mid,true);
        throw new BizException(1521,"引擎素材尚未生成成功或已不可用");
    }
    private static String value(JsonNode item,String component) {
        return switch(component) {
            case "VIDEO" -> item.path("urls").path("video_url").asText();
            case "COVER" -> item.path("urls").path("cover_url").asText();
            case "AUDIO" -> item.path("urls").path("voice_url").asText();
            case "TEXT" -> item.path("content").path("title").asText()+"\n"+item.path("content").path("text").asText();
            default -> {if(!component.matches("IMAGE_[0-9]{1,2}"))throw new BizException(1521,"引擎组件类型不支持");yield item.path("urls").path("image_urls").path(Integer.parseInt(component.substring(6))).asText();}
        };
    }
    private URI mediaUri(String url) {
        try {
            URI uri=URI.create(url);String configuredHost=URI.create(engine.base()).getHost();
            if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();
            for(var address:InetAddress.getAllByName(uri.getHost()))
                if((address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress())&&!uri.getHost().equalsIgnoreCase(configuredHost))throw new IllegalArgumentException();
            return uri;
        }catch(Exception e){throw new BizException(1521,"引擎素材访问地址不合法");}
    }
    public Asset register(TenantId tenant,UserId user,String request,String job,String mid,String component,String kind,boolean composite) {
        JsonNode item=material(tenant.value(),job,mid,false);String source=value(item,component);
        if(item.hasNonNull("store_id")&&!tenant.value().equals(item.path("store_id").asText()))throw new BizException(1521,"引擎素材租户归属不匹配");
        var location=item.path("storage").path(component);
        String bucket=null,physicalKey=null,endpoint=null,version="DB",etag=null;
        if(!"TEXT".equals(kind)) {
            bucket=location.path("bucket").asText(null);physicalKey=location.path("object_key").asText(null);endpoint=location.path("endpoint").asText(null);
            if(bucket==null&&properties!=null) {
                var uri=mediaUri(source);var config=properties.getStorage().getOss();
                String expected=com.lynx.ad.modules.creative.domain.service.AssetStoragePolicy.bucket(config,kind);
                if(uri.getHost().startsWith(expected+".")){bucket=expected;physicalKey=uri.getPath().replaceFirst("^/","");endpoint=config.getEndpoint();}
            }
            if(properties!=null && !java.util.Objects.equals(bucket,com.lynx.ad.modules.creative.domain.service.AssetStoragePolicy.bucket(properties.getStorage().getOss(),kind)))throw new BizException(1521,"引擎素材没有存入对应媒体桶");
            if(location.hasNonNull("store_id")&&!tenant.value().equals(location.path("store_id").asText()))throw new BizException(1521,"引擎存储租户不匹配");
            if(physicalKey!=null) {
                if(!mediaUri(source).getPath().endsWith("/"+physicalKey))throw new BizException(1521,"引擎素材地址与存储元信息不一致");
                version=physicalKey.startsWith("lynx-ad/v2/")?"V2":"LEGACY";
                if("V2".equals(version))com.lynx.ad.modules.creative.domain.service.AssetStoragePolicy.assertV2(tenant.value(),physicalKey);
                else if(!physicalKey.startsWith(tenant.value()+"/")||physicalKey.contains(".."))throw new BizException(1521,"引擎素材路径归属不匹配");
            }else if(properties!=null)throw new BizException(1521,"引擎未返回实际 OSS 路径");
        }
        // A material/component can point at a new engine object after a repair or revision.
        // Reuse only the same file version; retain old assets and their platform receipts.
        var legacy=repository.request(tenant.value(),request).orElse(null);
        if(legacy!=null) {
            var ref=repository.engineReference(tenant.value(),legacy.id());
            if(!ref.jobId().equals(job)||!ref.materialId().equals(mid)||!ref.component().equals(component)||!legacy.kind().equals(kind))throw new BizException(1520,"素材引用标识已用于其他产物");
            if("TEXT".equals(kind)?Objects.equals(legacy.textContent(),source):physicalKey!=null&&physicalKey.equals(legacy.objectKey()))return legacy;
        }
        String identity="TEXT".equals(kind)?source:bucket+":"+(physicalKey==null?source.split("\\?",2)[0]:physicalKey);
        request=UUID.nameUUIDFromBytes((request+":"+identity).getBytes(StandardCharsets.UTF_8)).toString();
        var existing=repository.request(tenant.value(),request).orElse(null);
        if(existing!=null)return existing;
        if(physicalKey!=null) {
            var previous=repository.storageObject(tenant.value(),physicalKey).orElse(null);
            if(previous!=null) {
                var provenance=repository.engineReference(tenant.value(),previous.id());
                if(!provenance.jobId().equals(job)||!provenance.materialId().equals(mid)||!provenance.component().equals(component))throw new BizException(1521,"引擎路径已关联其他组件");
                return previous;
            }
        }
        long size;String extension,mime;String text=null;
        Integer width=null,height=null;
        if("TEXT".equals(kind)) {
            if(!"TEXT".equals(component)||source.isBlank())throw new BizException(1521,"引擎文案缺失");
            text=source;size=source.getBytes(StandardCharsets.UTF_8).length;extension=".txt";mime="text/plain";
        }else {
            String expected=component.equals("COVER")||component.startsWith("IMAGE_")?"IMAGE":component;
            if(!kind.equals(expected))throw new BizException(1521,"引擎组件类型不匹配");
            try {
                // OSS signs the HTTP method. A GET-signed URL cannot be inspected with HEAD.
                // Read only one byte, and take the object size from Content-Range, not its body length.
                var meta=http.send(HttpRequest.newBuilder(mediaUri(source)).timeout(Duration.ofSeconds(30)).header("Range","bytes=0-0").GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(var body=meta.body()) {
                    if(meta.statusCode()==206) {
                        var range=java.util.regex.Pattern.compile("bytes 0-0/([1-9][0-9]*)").matcher(meta.headers().firstValue("Content-Range").orElse(""));
                        if(!range.matches())throw new BizException(1521,"引擎 OSS 未返回有效文件大小");
                        size=Long.parseLong(range.group(1));
                    }else if(meta.statusCode()==200)size=meta.headers().firstValueAsLong("Content-Length").orElse(0);
                    else throw new BizException(1521,"引擎 OSS 素材暂不可读取");
                    etag=meta.headers().firstValue("ETag").orElse(null);
                    mime=meta.headers().firstValue("Content-Type").orElse("").split(";")[0];
                }
                if(location.path("size").asLong(0)>0&&location.path("size").asLong()!=size)throw new BizException(1521,"引擎文件大小与元信息不一致");
                extension=switch(kind){case "VIDEO"->".mp4";case "IMAGE"->mime.equals("image/png")?".png":".jpg";case "AUDIO"->mime.contains("wav")?".wav":mime.contains("mp4")?".m4a":".mp3";default->throw new BizException(1521,"引擎组件类型不支持");};
                if(mime.isBlank()||mime.equals("application/octet-stream"))mime=switch(kind){case "VIDEO"->"video/mp4";case "IMAGE"->"image/jpeg";default->"audio/mpeg";};
                if(size<=0)throw new BizException(1521,"引擎 OSS 未返回有效文件大小");
            }catch(BizException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new BizException(1521,"读取引擎 OSS 元信息中断");}catch(Exception e){throw new BizException(1521,"读取引擎 OSS 元信息失败");}
            String resolution=item.path("detail").path(component.equals("COVER")?"cover_resolution":"resolution").asText();
            if(resolution.matches("[0-9]{1,5}[xX×][0-9]{1,5}")){String[] dimensions=resolution.split("[xX×]");width=Integer.valueOf(dimensions[0]);height=Integer.valueOf(dimensions[1]);}
        }
        String id=location.path("asset_id").asText("");
        if(!id.matches("[0-9a-fA-F-]{36}"))id=UUID.nameUUIDFromBytes((tenant.value()+":"+request).getBytes(StandardCharsets.UTF_8)).toString();
        var now=Instant.now();
        String key="TEXT".equals(kind)?null:physicalKey==null?id:physicalKey;
        double seconds=item.path("detail").path("duration_seconds").asDouble(0);
        Long duration=seconds>0?Math.round(seconds*1000):null;
        var metadata=new StorageMetadata(bucket,endpoint,version,"AI_GENERATED",duration,etag,null);
        var asset=new Asset(id,tenant.value(),user.value(),request,kind,"generated"+extension,mime,size,key,"STORED",MODE,null,null,location.path("md5").asText(null),true,width,height,text,null,null,now,now);
        try{repository.registerEngine(asset,new EngineReference(id,tenant.value(),job,mid,component,"TEXT".equals(kind)?null:source.split("\\?",2)[0]),metadata);}
        catch(DuplicateKeyException e){return repository.request(tenant.value(),request).orElseThrow(()->new BizException(1520,"引擎产物已登记，请重新查询"));}
        if(composite)repository.carouselComponent(tenant.value(),id);
        return repository.require(tenant.value(),id);
    }
    @Override public String getUrl(TenantId tenant,String key) {
        var ref=repository.engineReference(tenant.value(),assetId(tenant.value(),key));
        if("TEXT".equals(ref.component()))return "";
        if(located(tenant.value(),key))return objects.getUrl(tenant,key);
        String url=value(material(tenant.value(),ref.jobId(),ref.materialId(),false),ref.component());mediaUri(url);return url;
    }
    @Override public InputStream open(TenantId tenant,String key) {
        var ref=repository.engineReference(tenant.value(),assetId(tenant.value(),key));
        if("TEXT".equals(ref.component()))return new ByteArrayInputStream(repository.require(tenant.value(),key).textContent().getBytes(StandardCharsets.UTF_8));
        if(located(tenant.value(),key))return objects.open(tenant,key);
        try {
            String url=value(material(tenant.value(),ref.jobId(),ref.materialId(),true),ref.component());
            var response=http.send(HttpRequest.newBuilder(mediaUri(url)).timeout(Duration.ofSeconds(120)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            if(response.statusCode()!=200){response.body().close();throw new BizException(1521,"引擎 OSS 文件暂不可读取，请重试千川同步");}
            var body=response.body();var deadline=deadlines.schedule(()->{try{body.close();}catch(IOException ignored){}},120,TimeUnit.SECONDS);
            return new FilterInputStream(body){@Override public void close() throws IOException {deadline.cancel(false);super.close();}};
        }catch(BizException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new BizException(1521,"读取引擎 OSS 文件中断");}catch(Exception e){throw new BizException(1521,"读取引擎 OSS 文件失败");}
    }
    @Override public byte[] read(TenantId tenant,String key){throw new BizException(1521,"请通过流式接口读取引擎文件");}
    @Override public StorageObject put(TenantId tenant,String key,InputStream in,String type){throw new BizException(1521,"引擎 OSS 引用不支持重复写入");}
    @Override public void delete(TenantId tenant,String key){throw new BizException(1521,"引擎文件由引擎管理，仅可解除素材引用");}
    @PreDestroy public void close(){deadlines.shutdownNow();}
}
