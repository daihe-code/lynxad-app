package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LibraryMaterialStatisticsService {
    private final AssetUploadRepository assets;
    private final CarouselRepository carousels;
    private final CarouselPlatformService carouselPlatform;
    private final PlatformMaterialCatalog catalog;
    private final CreativeTaskRepository packages;
    private final QianchuanCredentialFacade credentials;
    private final MaterialLibraryPort library;
    private final AdManagementPort reports;
    public LibraryMaterialStatisticsService(AssetUploadRepository assets,CarouselRepository carousels,CarouselPlatformService carouselPlatform,
            PlatformMaterialCatalog catalog,CreativeTaskRepository packages,QianchuanCredentialFacade credentials,MaterialLibraryPort library,AdManagementPort reports) {
        this.assets=assets;this.carousels=carousels;this.carouselPlatform=carouselPlatform;this.catalog=catalog;
        this.packages=packages;this.credentials=credentials;this.library=library;this.reports=reports;
    }
    public record Result(String state,String accountId,String materialId,Map<String,BigDecimal> summary,
            List<AdManagementPort.MaterialDay> trend,boolean trendAvailable,Instant fetchedAt) {}
    private record Target(String account,String advertiser,String type,String object,String material) {}
    public Result query(TenantId tenant,String id,String account,PlanStatisticsRange range) {
        if(id==null || id.length()>320 || id.isBlank())throw invalid();
        var target=resolve(tenant,id,account);
        if(!Set.of("VIDEO","IMAGE","CAROUSEL").contains(target.type()))return empty("UNSUPPORTED",target.account());
        if(!present(target.account()))return empty("ACCOUNT_REQUIRED",null);
        // Recheck tenant ownership and active credentials on every read, including assets with no report yet.
        var c=credentials.getUsableCredential(tenant,new AccountId(target.account()));
        if(present(target.advertiser()) && !target.advertiser().equals(c.advertiserId().value()))throw invalid();
        String material=target.material();
        if(!numeric(material) && present(target.object()))material=materialId(tenant,c,target);
        if(!numeric(material))return empty("NO_DATA",target.account());
        var report=reports.libraryMaterialReport(c,target.type(),material,range.startDate(),range.endDate());
        return new Result(report.hasData()?"READY":"NO_DATA",target.account(),material,report.summary(),report.trend(),report.trendAvailable(),Instant.now());
    }
    private Target resolve(TenantId tenant,String id,String account) {
        String[] parts=id.split(":",4);
        if(parts.length==4 && "EXT".equals(parts[0])) {
            if(present(account) && !account.equals(parts[1]))throw invalid();
            MaterialType type;
            try {type=MaterialType.valueOf(parts[2]);}catch(IllegalArgumentException badType){throw invalid();}
            var entry=catalog.find(tenant.value(),parts[1],type,parts[3]).orElseThrow(LibraryMaterialStatisticsService::invalid);
            return new Target(entry.accountId(),entry.advertiserId(),type.name(),entry.objectId(),entry.materialId());
        }
        if(parts.length==2 && Set.of("UPLOAD","ENGINE").contains(parts[0])) {
            var asset=assets.require(tenant.value(),parts[1]);
            if("ENGINE".equals(parts[0]) != "engine_oss".equals(asset.storageMode()))throw invalid();
            if(present(asset.canonicalId()))asset=assets.require(tenant.value(),asset.canonicalId());
            var syncs=assets.syncs(tenant.value(),asset.id());
            if(!present(account)) {
                var choices=syncs.stream().filter(s->"UPLOADED".equals(s.status())).map(AssetUploadRepository.Sync::accountId).distinct().toList();
                if(choices.size()==1)account=choices.getFirst();
            }
            String selected=account;
            var sync=syncs.stream().filter(s->Objects.equals(selected,s.accountId()) && "UPLOADED".equals(s.status())).findFirst().orElse(null);
            return new Target(account,sync==null?null:sync.advertiserId(),asset.kind(),sync==null?null:sync.objectId(),sync==null?null:sync.materialId());
        }
        if(parts.length==2 && "CAROUSEL".equals(parts[0])) {
            var group=carousels.require(tenant.value(),parts[1]);
            if(!present(account) && group.accounts().size()==1)account=group.accounts().getFirst();
            if(!present(account))return new Target(null,null,"CAROUSEL",null,null);
            var receipt=carouselPlatform.receipt(tenant,group.id(),account);
            return new Target(account,null,"CAROUSEL",null,"SUCCEEDED".equals(receipt.status())?receipt.objectId():null);
        }
        if(parts.length==3 && "LYNX".equals(parts[0])) {
            var pkg=packages.findPackage(tenant,new PackageId(parts[1])).orElseThrow(LibraryMaterialStatisticsService::invalid);
            var art=pkg.items().stream().filter(a->!a.mockContent() && a.productId().value().equals(parts[2])).findFirst().orElseThrow(LibraryMaterialStatisticsService::invalid);
            return new Target(account,null,"VIDEO",art.videoId().isPresent()?art.videoId().orElseThrow().value():null,null);
        }
        throw invalid();
    }
    private String materialId(TenantId tenant,Credential c,Target target) {
        var type=MaterialType.valueOf(target.type());
        var cached=catalog.findObject(tenant.value(),target.account(),type,target.object());
        if(cached.isPresent() && c.advertiserId().value().equals(cached.get().advertiserId()) && numeric(cached.get().materialId()))return cached.get().materialId();
        if(type==MaterialType.CAROUSEL)return null;
        // Resolve the native material ID from the exact file ID, never a name or a local UUID.
        var page=library.list(new MaterialLibraryPort.MaterialLibraryQuery(c.advertiserId().value(),type,null,null,1,20,c.accessToken(),target.object(),null));
        var ids=page.items().stream().filter(m->target.object().equals(m.objectId()) && numeric(m.materialId())).map(MaterialLibraryPort.PlatformMaterial::materialId).distinct().toList();
        return ids.size()==1?ids.getFirst():null;
    }
    private static Result empty(String state,String account){return new Result(state,account,null,Map.of(),List.of(),false,Instant.now());}
    private static boolean present(String value){return value!=null && !value.isBlank();}
    private static boolean numeric(String value){return value!=null && value.matches("[1-9][0-9]*");}
    private static BizException invalid(){return new BizException(1522,"素材不存在或不属于当前账户");}
}
