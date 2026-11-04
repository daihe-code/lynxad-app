package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.domain.service.AssetFilePolicy;
import com.lynx.ad.modules.creative.domain.service.CarouselReviewPolicy;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CarouselService {
    private final CarouselRepository bundles;
    private final AssetUploadRepository assets;
    private final AssetUploadService uploads;
    private final OauthAccountRepository accounts;
    public CarouselService(CarouselRepository bundles, AssetUploadRepository assets, AssetUploadService uploads, OauthAccountRepository accounts) {
        this.bundles=bundles; this.assets=assets; this.uploads=uploads; this.accounts=accounts;
    }
    public record Create(String requestId, String name, String textAssetId, List<String> imageAssetIds, String audioAssetId, List<String> accountIds) {}
    public record Part(String role, int position, AssetUploadService.Detail asset) {}
    public record Detail(String id, String name, String createdAt, List<Part> parts, AssetUploadRepository.Review aiReview, List<String> accountIds) {}

    @Transactional
    public Detail create(TenantId tenant, Create request) {
        return create(tenant,TenantContext.userId(),request);
    }
    @Transactional
    public Detail create(TenantId tenant, UserId user, Create request) {
        if(request.requestId()==null || !request.requestId().matches("[A-Za-z0-9_-]{16,64}")) throw new BizException(1520,"图文请求标识不合法");
        String name=request.name()==null ? "" : request.name().trim();
        if(name.isBlank() || name.length()>100) throw new BizException(1520,"图文名称须为 1-100 个字符");
        var images=request.imageAssetIds()==null ? List.<String>of() : request.imageAssetIds();
        if(images.isEmpty() || images.size()>AssetFilePolicy.policy().carouselMaxImages() || new HashSet<>(images).size()!=images.size()) throw new BizException(1520,"图文需要 1-"+AssetFilePolicy.policy().carouselMaxImages()+" 张不同的图片");
        var targets=request.accountIds()==null ? List.<String>of() : request.accountIds().stream().distinct().toList();
        if(targets.size()>20) throw new BizException(1520,"一次最多同步 20 个账户");
        List<CarouselRepository.Part> parts=new ArrayList<>();
        if(request.textAssetId()!=null && !request.textAssetId().isBlank()) {
            parts.add(part(tenant,"TEXT",0,request.textAssetId()));
            var text=assets.require(tenant.value(),request.textAssetId());
            if(text.textContent()==null || text.textContent().isBlank()) throw new BizException(1520,"图文文本不能为空");
        }
        for(int i=0;i<images.size();i++) parts.add(part(tenant,"IMAGE",i,images.get(i)));
        parts.add(part(tenant,"AUDIO",0,request.audioAssetId()));
        var audio=assets.require(tenant.value(),request.audioAssetId());
        if(!Set.of("audio/mpeg","audio/mp4").contains(AssetFilePolicy.spec(audio.filename()).contentType()))
            throw new BizException(1520,"图文音频仅支持 MP3、M4A 格式");
        AssetFilePolicy.size(AssetFilePolicy.spec(audio.filename()),audio.sizeBytes());
        for(String target:targets) {
            if(target==null || target.isBlank()) throw new BizException(1201,"授权账户标识不合法");
            var account=accounts.findById(tenant,new AccountId(target)).orElseThrow(()->new BizException(1201,"账户不存在或不属于当前灵投账户"));
            if(account.platform()!=Platform.QIANCHUAN || "DELETED".equals(account.status())) throw new BizException(1201,"请选择有效千川授权账户");
        }
        var existing=bundles.request(tenant.value(),request.requestId());
        if(existing.isPresent()) {
            var b=existing.get();
            if(!b.name().equals(name) || !b.accounts().equals(targets) || !bundles.parts(b).equals(parts)) throw new BizException(1520,"图文请求标识已用于其他内容");
            return detail(tenant,b.id());
        }
        var bundle=new CarouselRepository.Bundle(UUID.randomUUID().toString(),tenant.value(),user.value(),request.requestId(),name,targets,"PENDING",Instant.now());
        bundles.insert(bundle,parts);
        refresh(bundle);
        return detail(tenant,bundle.id());
    }
    private CarouselRepository.Part part(TenantId tenant,String role,int position,String id) {
        if(id==null || id.isBlank()) throw new BizException(1520,"图文组件不能为空");
        var asset=assets.require(tenant.value(),id);
        if(!"STORED".equals(asset.storageStatus()) || !role.equals(asset.kind())) throw new BizException(1520,"图文组件未入库或类型不匹配");
        return new CarouselRepository.Part(role,position,id);
    }
    public Detail detail(TenantId tenant,String id) { return detail(bundles.require(tenant.value(),id)); }
    private Detail detail(CarouselRepository.Bundle bundle) {
        var tenant=new TenantId(bundle.tenant());
        var parts=bundles.parts(bundle).stream().map(p->new Part(p.role(),p.position(),uploads.detail(tenant,p.assetId()))).toList();
        var aggregate=CarouselReviewPolicy.aggregate(parts.stream().map(p->{
            var review=p.asset().aiReview();
            return new CarouselReviewPolicy.Part(label(p),review==null ? null : review.status(),review==null ? List.of() : review.reasons(),review==null ? null : review.errorMessage());
        }).toList());
        var review=new AssetUploadRepository.Review(bundle.id(),bundle.tenant(),aggregate.status(),null,
                aggregate.status(),aggregate.summary(),aggregate.reasons(),null,0,bundle.createdAt());
        return new Detail(bundle.id(),bundle.name(),bundle.createdAt().toString(),parts,review,bundle.accounts());
    }
    private static String label(Part part) { return switch(part.role()) { case "TEXT" -> "文本"; case "AUDIO" -> "音频"; default -> "图片 " + (part.position()+1); }; }
    public List<Detail> list(TenantId tenant) { return bundles.list(tenant.value()).stream().map(this::detail).toList(); }

    public record LibraryEntry(String id,String name,Instant createdAt,long sizeBytes,int imageCount,
            AssetUploadRepository.Asset cover,AssetUploadRepository.Review aiReview,String textContent,
            List<AssetUploadRepository.Sync> syncs) {}

    /** List metadata only. File URLs and full component details belong to the requested page/preview. */
    public List<LibraryEntry> library(TenantId tenant) {
        var groups=bundles.list(tenant.value());
        if(groups.isEmpty())return List.of();
        var parts=bundles.libraryParts(tenant.value());
        var requested=groups.stream().flatMap(g->parts.getOrDefault(g.id(),List.of()).stream()).map(CarouselRepository.Part::assetId).collect(java.util.stream.Collectors.toSet());
        var files=assets.libraryAssets(tenant.value(),requested);
        var canonical=files.values().stream().map(AssetUploadRepository.Asset::canonicalId).filter(Objects::nonNull).filter(id->!files.containsKey(id)).toList();
        files.putAll(assets.libraryAssets(tenant.value(),canonical));
        var decoration=assets.componentLibraryDetails(tenant.value(),new ArrayList<>(files.values()));
        var result=new ArrayList<LibraryEntry>();
        for(var group:groups) {
            var componentParts=parts.getOrDefault(group.id(),List.of());
            var checks=new ArrayList<CarouselReviewPolicy.Part>();var syncs=new ArrayList<AssetUploadRepository.Sync>();
            AssetUploadRepository.Asset cover=null;long size=0;int imageCount=0;String text="";
            for(var part:componentParts) {
                var file=files.get(part.assetId());
                if(file!=null && file.canonicalId()!=null)file=files.get(file.canonicalId());
                var review=file==null ? null : decoration.reviews().get(file.id());
                String label="TEXT".equals(part.role()) ? "文本" : "AUDIO".equals(part.role()) ? "音频" : "图片 "+(part.position()+1);
                checks.add(new CarouselReviewPolicy.Part(label,review==null?null:review.status(),review==null?List.of():review.reasons(),review==null?null:review.errorMessage()));
                if("IMAGE".equals(part.role())) { imageCount++; if(cover==null)cover=file; }
                if(file==null)continue;
                size+=file.sizeBytes();
                if("TEXT".equals(part.role()))text=Objects.toString(file.textContent(),"");
                decoration.syncs().getOrDefault(file.id(),List.of()).stream().filter(s->group.accounts().contains(s.accountId())).forEach(syncs::add);
            }
            var aggregate=CarouselReviewPolicy.aggregate(checks);
            var review=new AssetUploadRepository.Review(group.id(),group.tenant(),aggregate.status(),null,aggregate.status(),aggregate.summary(),aggregate.reasons(),null,0,group.createdAt());
            result.add(new LibraryEntry(group.id(),group.name(),group.createdAt(),size,imageCount,cover,review,text,List.copyOf(syncs)));
        }
        return result;
    }

    /** No component platform tasks are created until the entire composite has passed. */
    @Transactional
    public void refresh(CarouselRepository.Bundle bundle) {
        var detail=detail(bundle);
        if(Set.of("APPROVED","NOT_REQUIRED").contains(detail.aiReview().status())) for(var part:detail.parts()) {
            if("TEXT".equals(part.role())) continue;
            var asset=assets.require(bundle.tenant(),part.asset().id());
            for(String target:bundle.accounts()) assets.addSync(asset,target);
            assets.activate(bundle.tenant(),asset.id());
        }
        bundles.reviewed(bundle,detail.aiReview().status());
    }
}
