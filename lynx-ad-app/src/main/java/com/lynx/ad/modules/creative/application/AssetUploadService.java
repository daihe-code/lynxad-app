package com.lynx.ad.modules.creative.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.domain.port.RoutedStoragePort;
import com.lynx.ad.modules.creative.domain.service.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Asset;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetUploadService {
    private final AssetUploadRepository repository;
    private final StoragePort storage;
    private final OauthAccountRepository accounts;
    private final LynxAdProperties properties;
    private final ObjectMapper mapper;
    private final MediaFileInspector inspector;
    public AssetUploadService(AssetUploadRepository repository, StoragePort storage, OauthAccountRepository accounts, LynxAdProperties properties, ObjectMapper mapper, MediaFileInspector inspector) {
        this.repository=repository; this.storage=storage; this.accounts=accounts; this.properties=properties; this.mapper=mapper;
        this.inspector=inspector;
    }
    public record InitRequest(String requestId, String filename, long sizeBytes, boolean isAigc, List<String> accountIds, String libraryType) {
        public InitRequest(String requestId, String filename, long sizeBytes, boolean isAigc, List<String> accountIds) {
            this(requestId, filename, sizeBytes, isAigc, accountIds, null);
        }
    }
    public record InitResult(String assetId, String storageStatus, String uploadMode, StoragePort.UploadTicket ticket) {}
    public record Detail(String id, String kind, String filename, long sizeBytes, String storageStatus, String storageMode,
            boolean isAigc, String md5, Integer width, Integer height, String textContent, String url, String errorMessage,
            String createdAt, List<AssetUploadRepository.Sync> syncs, AssetUploadRepository.Review aiReview, AssetUploadRepository.StorageMetadata storageMetadata) {
        public Detail(String id,String kind,String filename,long sizeBytes,String storageStatus,String storageMode,boolean isAigc,String md5,Integer width,Integer height,String textContent,String url,String errorMessage,String createdAt,List<AssetUploadRepository.Sync> syncs,AssetUploadRepository.Review aiReview){this(id,kind,filename,sizeBytes,storageStatus,storageMode,isAigc,md5,width,height,textContent,url,errorMessage,createdAt,syncs,aiReview,null);}
    }

    /** User-entered text follows upload validation and review; engine outputs use EngineAssetStorage references. */
    public Detail ingest(TenantId tenant, UserId user, String requestId, Path file, String filename, boolean aigc, String account, boolean component) {
        long size;
        try { size=Files.size(file); } catch(IOException e) { throw new BizException(1521,"引擎产物读取失败"); }
        var spec=AssetFilePolicy.spec(filename); AssetFilePolicy.size(spec,size);
        if(account!=null) {
            var bound=accounts.findById(tenant,new AccountId(account)).orElseThrow(()->new BizException(1201,"授权账户不属于当前坤境账户"));
            if(bound.platform()!=Platform.QIANCHUAN) throw new BizException(1201,"请选择千川账户");
            AssetFilePolicy.target(filename,true);
        }
        Asset a=repository.request(tenant.value(),requestId).orElse(null);
        if(a!=null && ("STORED".equals(a.storageStatus()) || a.canonicalId()!=null)) { if(account!=null) ensureTarget(tenant,a.id(),account); return detail(tenant,a.id()); }
        if(a==null) {
            String id=UUID.randomUUID().toString();var now=Instant.now();
            a=new Asset(id,tenant.value(),user.value(),requestId,spec.kind().name(),filename,spec.contentType(),size,
                    spec.kind()==AssetKind.TEXT?null:AssetStoragePolicy.key(tenant,id,filename),
                    "WAITING",properties.getStorage().getAssetMode(),null,null,null,aigc,null,null,null,null,null,now,now);
            repository.insert(a); initializeStorage(a);
            if(component) repository.carouselComponent(tenant.value(),a.id());
        }
        if(account!=null && spec.kind()!=AssetKind.TEXT) repository.addSync(a,account);
        if(!repository.claimUpload(tenant.value(),a.id())) throw new BizException(1520,"产物正在入库，请稍后查询");
        try {
            var checked=inspect(file,a,AssetFilePolicy.validate(file,filename,size));
            var existing=repository.content(tenant.value(),AssetUploadRepository.contentKey(a,checked.md5())).orElse(null);
            if(existing!=null) return duplicate(tenant,a,existing);
            if(spec.kind()!=AssetKind.TEXT)try(var in=Files.newInputStream(file)){location(a).put(tenant,a.objectKey(),in,size,spec.contentType());}
            return finalizeStored(tenant,a,checked);
        }catch(BizException e){repository.storageFailed(a,e.getMessage());throw e;}
        catch(Exception e){repository.storageFailed(a,"引擎产物入库失败");throw new BizException(1521,"引擎产物入库失败");}
    }

    public void queueUnassigned() {for(var asset:repository.unassigned())queueDefaultTarget(asset);}
    private void queueDefaultTarget(Asset asset) {
        if("TEXT".equals(asset.kind())||repository.isCarouselComponent(asset.tenantId(),asset.id())||!repository.syncs(asset.tenantId(),asset.id()).isEmpty())return;
        // A single usable authorization is unambiguous. Multiple advertisers require a selection.
        var tenant=new TenantId(asset.tenantId());
        var bound=accounts.findByTenant(tenant).stream().filter(a->a.platform()==Platform.QIANCHUAN&&"ACTIVE".equals(a.status())).toList();
        if(bound.size()!=1)return;
        try{AssetFilePolicy.target(asset.filename(),true);}catch(BizException incompatible){return;}
        repository.addSync(asset,bound.getFirst().id().value());repository.activate(asset.tenantId(),asset.id());
    }
    public void attachPreview(TenantId tenant,String id,String preview){repository.preview(tenant.value(),id,preview);}

    /** Reuse a bound cover, or derive one from the owned video and use the normal review pipeline. */
    public String videoCover(TenantId tenant,UserId user,String id) {
        var video=repository.require(tenant.value(),id);
        if(video.canonicalId()!=null)video=repository.require(tenant.value(),video.canonicalId());
        if(!"VIDEO".equals(video.kind())||!"STORED".equals(video.storageStatus()))throw new BizException(1520,"请选择已上传的视频");
        var meta=repository.storageMetadata(tenant.value(),video.id());
        if(meta!=null&&meta.previewAssetId()!=null) {
            var cover=repository.require(tenant.value(),meta.previewAssetId());
            if("IMAGE".equals(cover.kind())&&"STORED".equals(cover.storageStatus()))return cover.id();
        }
        String request="video-cover:"+video.id();
        var existing=repository.request(tenant.value(),request).orElse(null);
        if(existing!=null&&("STORED".equals(existing.storageStatus())||existing.canonicalId()!=null)) {
            String cover=detail(tenant,existing.id()).id();attachPreview(tenant,video.id(),cover);return cover;
        }
        Path file=null,image=null;
        try {
            file=download(tenant,video);image=Files.createTempFile("lynx-video-cover-",".jpg");inspector.cover(file,image);
            var cover=ingest(tenant,user,request,image,"视频首帧封面.jpg",video.isAigc(),null,false);
            attachPreview(tenant,video.id(),cover.id());return cover.id();
        }catch(IOException ex){throw new BizException(1521,"视频封面处理失败，请重试");}
        finally{remove(file);remove(image);}
    }

    public void ensureTarget(TenantId tenant,String id,String account) {
        var a=repository.require(tenant.value(),id);
        if(a.canonicalId()!=null)a=repository.require(tenant.value(),a.canonicalId());
        var bound=accounts.findById(tenant,new AccountId(account)).orElseThrow(()->new BizException(1201,"授权账户不属于当前坤境账户"));
        if(bound.platform()!=Platform.QIANCHUAN) throw new BizException(1201,"请选择千川账户");
        AssetFilePolicy.target(a.filename(),true);
        if("TEXT".equals(a.kind()))return;
        a=ensureReviewStorage(tenant,a);repository.addSync(a,account);repository.activate(tenant.value(),a.id());
    }

    public InitResult init(TenantId tenant, InitRequest request) {
        request = new InitRequest(request.requestId(),request.filename(),request.sizeBytes(),request.isAigc(),request.accountIds(),
                request.libraryType()==null ? null : request.libraryType().toUpperCase(Locale.ROOT));
        if (request.requestId()==null || !request.requestId().matches("[A-Za-z0-9_-]{16,64}")) throw new BizException(1520,"上传请求标识不合法");
        String filename = request.filename();
        if (filename==null || filename.isBlank() || filename.length()>255 || filename.chars().anyMatch(c->c=='/' || c=='\\' || Character.isISOControl(c))) throw new BizException(1520,"文件名称不合法或过长");
        var spec=AssetFilePolicy.spec(filename); AssetFilePolicy.size(spec,request.sizeBytes());
        AssetFilePolicy.library(spec.kind(), request.libraryType());
        if ("CAROUSEL".equals(request.libraryType()) && request.accountIds()!=null && !request.accountIds().isEmpty())
            throw new BizException(1520,"图文组件须先统一质检，请在保存整条图文时选择同步账户");
        List<String> targets = request.accountIds()==null ? List.of() : request.accountIds().stream().distinct().toList();
        AssetFilePolicy.target(filename,!targets.isEmpty());
        if (targets.size()>20) throw new BizException(1520,"一次最多同步 20 个授权账户");
        // A canceled/expired account can retain the file; the worker will explicitly wait for authorization.
        for (String account:targets) {
            if (account==null || account.isBlank()) throw new BizException(1201,"授权账户标识不合法");
            var bound=accounts.findById(tenant,new AccountId(account)).orElseThrow(() -> new BizException(1201,"授权账户不存在或不属于当前坤境账户"));
            if (bound.platform()!=Platform.QIANCHUAN || "DELETED".equals(bound.status())) throw new BizException(1201,"请选择有效的千川授权记录");
        }
        Asset a=repository.request(tenant.value(),request.requestId()).orElse(null);
        if (a==null) {
            var now=Instant.now(); String id=UUID.randomUUID().toString();
            a=new Asset(id,tenant.value(),TenantContext.userId().value(),request.requestId(),spec.kind().name(),filename,spec.contentType(),request.sizeBytes(),
                spec.kind()==AssetKind.TEXT?null:AssetStoragePolicy.key(tenant,id,filename),"WAITING",properties.getStorage().getAssetMode(),null,null,null,request.isAigc(),null,null,null,null,null,now,now);
            try { repository.insert(a); initializeStorage(a); if("CAROUSEL".equals(request.libraryType())) repository.carouselComponent(tenant.value(),a.id()); }
            catch (DuplicateKeyException ex) { a=repository.request(tenant.value(),request.requestId()).orElseThrow(); }
        }
        initializeStorage(a);
        if (!a.filename().equals(filename) || a.sizeBytes()!=request.sizeBytes() || a.isAigc()!=request.isAigc()) throw new BizException(1520,"上传请求标识已用于其他文件");
        if (a.canonicalId()!=null) a=repository.require(tenant.value(),a.canonicalId());
        if (!"CAROUSEL".equals(request.libraryType())) repository.exposeStandalone(tenant.value(),a.id());
        if ("EXPIRED".equals(a.storageStatus())) throw new BizException(1520,"上传会话已过期，请重新选择文件");
        if (spec.kind()!=AssetKind.TEXT) for (String account:targets) repository.addSync(a,account);
        if ("STORED".equals(a.storageStatus())) {
            a=ensureReviewStorage(tenant,a); repository.queueReview(tenant.value(),a.id()); repository.activate(tenant.value(),a.id());
            return new InitResult(a.id(),"STORED","NONE",null);
        }
        StoragePort.UploadTicket ticket=null;
        if (spec.kind()!=AssetKind.TEXT && "oss".equalsIgnoreCase(a.storageMode())) {
            try {
                if (a.uploadTicketJson()!=null) ticket=mapper.readValue(a.uploadTicketJson(),StoragePort.UploadTicket.class);
                else {
                    ticket=location(a).beginUpload(tenant,a.objectKey(),a.sizeBytes(),a.contentType());
                    if (ticket!=null && !repository.ticket(tenant.value(),a.id(),ticket.uploadId(),mapper.writeValueAsString(ticket))) {
                        location(a).abortUpload(tenant,a.objectKey(),ticket.uploadId());
                        var current=repository.require(tenant.value(),a.id());
                        if(current.uploadTicketJson()==null) throw new BizException(1520,"上传会话已过期或取消");
                        ticket=mapper.readValue(current.uploadTicketJson(),StoragePort.UploadTicket.class);
                    }
                }
            } catch (Exception ex) { repository.storageFailed(a,"平台 OSS 上传初始化失败，请检查 Endpoint、Bucket 和存储凭证"); throw new BizException(1521,"平台 OSS 上传初始化失败，请检查 Endpoint、Bucket 和存储凭证"); }
        }
        return new InitResult(a.id(),a.storageStatus(),ticket==null ? "PROXY" : "OSS_MULTIPART",ticket);
    }
    public Detail upload(TenantId tenant, String id, MultipartFile file) {
        Asset a=repository.require(tenant.value(),id);
        if (a.canonicalId()!=null || "STORED".equals(a.storageStatus())) return detail(tenant,id);
        if (a.uploadId()!=null) throw new BizException(1520,"该文件应通过 OSS 分片上传");
        if (file.getSize()!=a.sizeBytes()) throw new BizException(1520,"实际文件大小与声明不一致");
        if (!repository.claimUpload(tenant.value(),id)) throw new BizException(1520,"文件正在上传或会话已过期");
        Path temp=null;
        try {
            temp=Files.createTempFile("lynx-asset-", ".tmp");
            try (InputStream in=file.getInputStream()) { Files.copy(in,temp,StandardCopyOption.REPLACE_EXISTING); }
            repository.validating(a);
            var validated=AssetFilePolicy.validate(temp,a.filename(),a.sizeBytes());
            validated=inspect(temp,a,validated);
            Asset existing=repository.content(tenant.value(),AssetUploadRepository.contentKey(a,validated.md5())).orElse(null);
            if (existing!=null) return duplicate(tenant,a,existing);
            if(!"TEXT".equals(a.kind()))try (InputStream in=Files.newInputStream(temp)) { location(a).put(tenant,a.objectKey(),in,a.sizeBytes(),a.contentType()); }
            return finalizeStored(tenant,a,validated);
        } catch (BizException ex) { repository.storageFailed(a,ex.getMessage()); throw ex; }
        catch (Exception ex) { repository.storageFailed(a,"平台文件存储失败，请重试"); throw new BizException(1521,"平台文件存储失败，请重试"); }
        finally { remove(temp); }
    }
    public Detail complete(TenantId tenant, String id, List<StoragePort.UploadPart> parts) {
        Asset a=repository.require(tenant.value(),id);
        if (a.canonicalId()!=null || "STORED".equals(a.storageStatus())) return detail(tenant,id);
        if (a.uploadId()==null || parts==null || parts.isEmpty()) throw new BizException(1520,"OSS 上传会话或分片信息缺失");
        if (!repository.claimUpload(tenant.value(),id)) throw new BizException(1520,"文件正在校验或会话已过期");
        Path temp=null;
        try {
            location(a).completeUpload(tenant,a.objectKey(),a.uploadId(),parts,a.sizeBytes());
            repository.validating(a);
            temp=download(tenant,a);
            var validated=AssetFilePolicy.validate(temp,a.filename(),a.sizeBytes());
            validated=inspect(temp,a,validated);
            Asset existing=repository.content(tenant.value(),AssetUploadRepository.contentKey(a,validated.md5())).orElse(null);
            if (existing!=null) return duplicate(tenant,a,existing);
            return finalizeStored(tenant,a,validated);
        } catch (BizException ex) { repository.storageFailed(a,ex.getMessage()); throw ex; }
        catch (Exception ex) { repository.storageFailed(a,"OSS 分片合并或文件校验失败，请重新上传"); throw new BizException(1521,"OSS 分片合并或文件校验失败，请重新上传"); }
        finally { remove(temp); }
    }
    private void initializeStorage(Asset a) {
        var meta=repository.storageMetadata(a.tenantId(),a.id());
        if(meta!=null && (meta.bucket()!=null || "DB".equals(meta.pathVersion())))return;
        var config=properties.getStorage().getOss();boolean v2=a.objectKey()!=null&&a.objectKey().startsWith("lynx-ad/v2/");
        repository.storageMetadata(a.tenantId(),a.id(),"TEXT".equals(a.kind())?null:v2?AssetStoragePolicy.bucket(config,a.kind()):config.getBucket(),config.getEndpoint(),"TEXT".equals(a.kind())?"DB":v2?"V2":"LEGACY","LOCAL_UPLOAD");
    }
    private StoragePort location(Asset asset) { return RoutedStoragePort.select(storage,asset.storageMode()); }
    private AssetFilePolicy.Validated inspect(Path temp, Asset a, AssetFilePolicy.Validated validated) {
        var media=inspector.validate(temp,a.filename());
        if(media!=null)repository.duration(a.tenantId(),a.id(),Math.round(media.durationSeconds()*1000));
        return media==null ? validated : new AssetFilePolicy.Validated(validated.md5(),validated.size(),media.width(),media.height(),validated.text());
    }
    private Detail finalizeStored(TenantId tenant, Asset a, AssetFilePolicy.Validated v) {
        try { repository.stored(a,v.md5(),v.width(),v.height(),v.text()); }
        catch (DuplicateKeyException ex) { return duplicate(tenant,a,repository.content(tenant.value(),AssetUploadRepository.contentKey(a,v.md5())).orElseThrow()); }
        repository.queueReview(tenant.value(),a.id()); queueDefaultTarget(a); repository.activate(tenant.value(),a.id()); return detail(tenant,a.id());
    }
    private Detail duplicate(TenantId tenant, Asset a, Asset existing) {
        existing=ensureReviewStorage(tenant,existing);
        if(!repository.isCarouselComponent(tenant.value(),a.id())) repository.exposeStandalone(tenant.value(),existing.id());
        for (var sync:repository.syncs(tenant.value(),a.id())) repository.addSync(existing,sync.accountId());
        repository.duplicate(a,existing.id()); repository.queueReview(tenant.value(),existing.id()); repository.activate(tenant.value(),existing.id());
        repository.removeDuplicateTasks(a);
        try { location(a).delete(tenant,a.objectKey()); } catch (Exception ignored) {}
        return detail(tenant,existing.id());
    }
    public Detail detail(TenantId tenant, String id) {
        Asset a=repository.require(tenant.value(),id);
        if (a.canonicalId()!=null) a=repository.require(tenant.value(),a.canonicalId());
        return new Detail(a.id(),a.kind(),a.filename(),a.sizeBytes(),a.storageStatus(),a.storageMode(),a.isAigc(),a.md5(),a.width(),a.height(),a.textContent(),
            "STORED".equals(a.storageStatus()) && !"TEXT".equals(a.kind()) ? location(a).getUrl(tenant,a.objectKey()) : "",a.errorMessage(),a.createdAt().toString(),repository.syncs(tenant.value(),a.id()),repository.aiReview(tenant.value(),a.id()),repository.storageMetadata(tenant.value(),a.id()));
    }
    public Map<String,String> previewImages(TenantId tenant, List<String> ids) {
        Map<String,String> urls=new HashMap<>();
        for(var asset:repository.storedImages(tenant.value(),ids))
            try{urls.put(asset.id(),location(asset).getUrl(tenant,asset.objectKey()));}
            catch(RuntimeException unavailable){/* A missing optional cover must not block the plan list. */}
        return urls;
    }
    /** Resolve the file actually uploaded under this platform ID, independent of later regenerations. */
    public Optional<String> platformPreviewUrl(TenantId tenant,String account,String kind,String objectId) {
        if(objectId==null || objectId.isBlank() || !Set.of("VIDEO","AUDIO").contains(kind))return Optional.empty();
        return repository.platformObject(tenant.value(),account,kind,objectId).map(a->location(a).getUrl(tenant,a.objectKey()));
    }
    public boolean ownsUploadedVideo(TenantId tenant,String account,String videoId) {
        return videoId!=null && !videoId.isBlank() && repository.platformObject(tenant.value(),account,"VIDEO",videoId).isPresent();
    }
    public Detail retry(TenantId tenant, String id, String account, boolean acknowledged) {
        var asset=repository.require(tenant.value(),id);
        if(acknowledged)throw new BizException(1523,"待确认任务只能核查结果，不可直接重新上传");
        if (!"STORED".equals(asset.storageStatus())) throw new BizException(1520,"文件尚未入库");
        if (!repository.approved(tenant.value(),id)) throw new BizException(1525,"AI 审核尚未通过，不能同步千川，请先查看审核结果");
        AssetFilePolicy.target(asset.filename(),true);
        if (account!=null && !account.isBlank()) {
            var bound=accounts.findById(tenant,new AccountId(account)).orElseThrow(() -> new BizException(1201,"授权账户不存在或不属于当前坤境账户"));
            if(bound.platform()!=Platform.QIANCHUAN||"DELETED".equals(bound.status()))throw new BizException(1201,"请选择千川授权账户");
            repository.addSync(asset,account);repository.activate(tenant.value(),asset.id());
        }
        repository.retry(tenant.value(),id,account,acknowledged); return detail(tenant,id);
    }
    public void cancel(TenantId tenant, String id) {
        var a=repository.require(tenant.value(),id);
        if ("STORED".equals(a.storageStatus()) || "DUPLICATE".equals(a.storageStatus())) return;
        if (a.uploadId()!=null) try { location(a).abortUpload(tenant,a.objectKey(),a.uploadId()); } catch (Exception ignored) {}
        try { location(a).delete(tenant,a.objectKey()); } catch (Exception ignored) {}
        repository.expired(a);
    }
    public Detail retryReview(TenantId tenant,String id) {
        var asset=repository.require(tenant.value(),id);
        if(asset.canonicalId()!=null) asset=repository.require(tenant.value(),asset.canonicalId());
        if(!"STORED".equals(asset.storageStatus())) throw new BizException(1525,"文件尚未入库");
        if(repository.isEngineAsset(tenant.value(),asset.id())) throw new BizException(1525,"AI 引擎生成素材无需上传审核");
        asset=ensureReviewStorage(tenant,asset);
        repository.queueReview(tenant.value(),asset.id()); repository.retryReview(tenant.value(),asset.id());
        return detail(tenant,asset.id());
    }
    /** A review retry can recover existing local uploads without asking users to upload the file again. */
    private Asset ensureReviewStorage(TenantId tenant, Asset asset) {
        if(!"local".equalsIgnoreCase(asset.storageMode()) || !"oss".equalsIgnoreCase(properties.getStorage().getAssetMode()) || AssetKind.TEXT.name().equals(asset.kind())) return asset;
        Path temp=null;
        try {
            temp=download(tenant,asset);
            var validated=AssetFilePolicy.validate(temp,asset.filename(),asset.sizeBytes());
            inspect(temp,asset,validated);
            if(!validated.md5().equals(asset.md5())) throw new BizException(1520,"本地素材内容与原记录不一致，请重新上传");
            try(var in=Files.newInputStream(temp)) {
                RoutedStoragePort.select(storage,"oss").put(tenant,asset.objectKey(),in,asset.sizeBytes(),asset.contentType());
            }
            repository.promotedToOss(tenant.value(),asset.id());
            // A failed review based on a local URL can now be retried with the OSS URL.
            repository.retryReview(tenant.value(),asset.id());
            return repository.require(tenant.value(),asset.id());
        } catch(BizException ex) { throw ex; }
        catch(Exception ex) { throw new BizException(1521,"素材仍保存在本地；转存 OSS 失败，请检查平台存储配置后重试审核"); }
        finally { remove(temp); }
    }
    public Path download(TenantId tenant, Asset a) throws IOException {
        Path temp=Files.createTempFile("lynx-asset-sync-", ".tmp");
        try (InputStream in="TEXT".equals(a.kind())?new ByteArrayInputStream(a.textContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)):location(a).open(tenant,a.objectKey()); var out=Files.newOutputStream(temp)) {
            byte[] buffer=new byte[64*1024]; long total=0; int read;
            while ((read=in.read(buffer))!=-1) { total+=read; if(total>a.sizeBytes()) throw new BizException(1520,"存储文件大小不一致"); out.write(buffer,0,read); }
            if(total!=a.sizeBytes()) throw new BizException(1520,"存储文件大小不一致");
            return temp;
        } catch (Exception ex) { remove(temp); throw ex; }
    }
    private Optional<Asset> syncRepair(TenantId tenant,Asset source) {
        return repository.request(tenant.value(),"sync-format-v1:"+source.id()).map(a->a.canonicalId()==null?a:repository.require(tenant.value(),a.canonicalId())).filter(a->"STORED".equals(a.storageStatus()));
    }
    public Asset syncPayloadForSignature(TenantId tenant,Asset source,String signature) {
        if(signature!=null&&signature.equalsIgnoreCase(Objects.toString(source.md5(),"")))return source;
        return syncRepair(tenant,source).filter(a->signature!=null&&signature.equalsIgnoreCase(Objects.toString(a.md5(),""))).orElse(null);
    }
    public Asset syncPayload(TenantId tenant,Asset source,boolean repair) throws IOException {
        var previous=syncRepair(tenant,source);if(previous.isPresent())return previous.get();
        if(!repair)return source;
        if(!repository.approved(tenant.value(),source.id()))throw new BizException(1525,"源素材尚未通过内容检查");
        String filename="compatible."+switch(source.kind()){case "VIDEO"->"mp4";case "IMAGE"->"jpg";case "AUDIO"->"mp3";default->throw new BizException(1520,"不支持修复此文件类型");};
        Path original=null,converted=null;Asset prepared=null;boolean owned=false;
        try {
            original=download(tenant,source);
            if(source.md5()!=null&&!source.md5().equalsIgnoreCase(fileSignature(original)))throw new BizException(1520,"源素材文件与已检查内容不一致");
            converted=Files.createTempFile("lynx-sync-format-","-"+filename);inspector.repairForUpload(original,converted,source.kind());
            var validated=AssetFilePolicy.validate(converted,filename,Files.size(converted));var media=inspector.validate(converted,filename);
            if(media!=null)validated=new AssetFilePolicy.Validated(validated.md5(),validated.size(),media.width(),media.height(),null);
            var duplicate=repository.content(tenant.value(),AssetUploadRepository.contentKey(source,validated.md5())).orElse(null);
            if(duplicate!=null&&duplicate.id().equals(source.id()))return source;
            var a=repository.request(tenant.value(),"sync-format-v1:"+source.id()).orElse(null);
            if(a==null){String id=UUID.randomUUID().toString();var now=Instant.now();a=new Asset(id,tenant.value(),source.userId(),"sync-format-v1:"+source.id(),source.kind(),filename,AssetFilePolicy.spec(filename).contentType(),validated.size(),AssetStoragePolicy.key(tenant,id,filename),"WAITING",properties.getStorage().getAssetMode(),null,null,null,source.isAigc(),null,null,null,null,null,now,now);repository.insert(a);initializeStorage(a);repository.carouselComponent(tenant.value(),id);}
            prepared=a;
            if(!repository.claimUpload(tenant.value(),a.id()))throw new BizException(1526,"素材格式处理正在完成");
            owned=true;
            try(var in=Files.newInputStream(converted)){location(a).put(tenant,a.objectKey(),in,validated.size(),a.contentType());}
            if(repository.aiReview(tenant.value(),a.id())==null)repository.approveTechnicalRepair(tenant.value(),source.id(),a.id());
            try{repository.stored(a,validated.md5(),validated.width(),validated.height(),null);}catch(DuplicateKeyException same){var canonical=repository.content(tenant.value(),AssetUploadRepository.contentKey(a,validated.md5())).orElseThrow();repository.duplicate(a,canonical.id());return canonical;}
            if(media!=null)repository.duration(tenant.value(),a.id(),Math.round(media.durationSeconds()*1000));
            return repository.require(tenant.value(),a.id());
        }catch(IOException|RuntimeException ex){if(owned)repository.storageFailed(prepared,"素材格式处理未完成，等待后台重试");throw ex;}
        finally{remove(original);remove(converted);}
    }
    private static String fileSignature(Path file) throws IOException {
        try {
            var digest=java.security.MessageDigest.getInstance("MD5");
            try(var input=new java.security.DigestInputStream(Files.newInputStream(file),digest)){input.transferTo(OutputStream.nullOutputStream());}
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static void remove(Path path) { if (path!=null) try { Files.deleteIfExists(path); } catch (IOException ignored) {} }
}
