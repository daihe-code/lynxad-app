package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.identity.domain.model.Credential;

/** Metadata GET only; implementations must never upload, create, generate or download media. */
public interface AssetReceiptLookupPort {
    record Expected(AssetKind kind,String signature,long sizeBytes,Integer width,Integer height,boolean aigc,String objectId,String materialId) {
        public Expected(AssetKind kind,String signature,long sizeBytes,Integer width,Integer height,boolean aigc){this(kind,signature,sizeBytes,width,height,aigc,null,null);}
    }
    record Result(String state,String objectId,String materialId) {
        public static Result pending(String state){return new Result(state,null,null);}
    }
    Result find(Credential credential,Expected expected);
}
