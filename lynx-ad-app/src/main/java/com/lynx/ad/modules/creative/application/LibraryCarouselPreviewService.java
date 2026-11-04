package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import org.springframework.stereotype.Service;

@Service
public class LibraryCarouselPreviewService {
    private final PlatformMaterialCatalog catalog;
    private final QianchuanCredentialFacade credentials;
    private final MaterialLibraryPort library;
    public LibraryCarouselPreviewService(PlatformMaterialCatalog catalog,QianchuanCredentialFacade credentials,MaterialLibraryPort library) {
        this.catalog=catalog;this.credentials=credentials;this.library=library;
    }
    public MaterialLibraryPort.CarouselMedia preview(TenantId tenant,String id) {
        String[] parts=id==null?new String[0]:id.split(":",4);
        if(id==null || id.length()>320 || parts.length!=4 || !"EXT".equals(parts[0]) || !"CAROUSEL".equals(parts[2]))throw invalid();
        var entry=catalog.find(tenant.value(),parts[1],MaterialType.CAROUSEL,parts[3]).orElseThrow(LibraryCarouselPreviewService::invalid);
        var c=credentials.getUsableCredential(tenant,new AccountId(entry.accountId()));
        if(!c.advertiserId().value().equals(entry.advertiserId()))throw invalid();
        return library.carouselMedia(new MaterialLibraryPort.MaterialLibraryQuery(entry.advertiserId(),MaterialType.CAROUSEL,null,null,1,100,c.accessToken(),null,entry.materialId()));
    }
    private static BizException invalid() { return new BizException(1522,"图文不存在或不属于当前账户"); }
}
