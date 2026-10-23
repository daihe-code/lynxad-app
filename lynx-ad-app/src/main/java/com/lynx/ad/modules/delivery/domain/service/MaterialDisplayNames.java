package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Management labels are independent of the advertising titles used by previews and publication. */
public final class MaterialDisplayNames {
    private MaterialDisplayNames() {}

    public static Map<String,String> of(CreationState state) {
        var names=new LinkedHashMap<String,String>();var used=new HashSet<String>();
        for(var material:state.materials) {
            if(material.engineMaterialId==null||material.engineMaterialId.isBlank()) {
                names.put(material.id,material.title);continue;
            }
            String type=switch(Objects.toString(material.type,"")){case "VIDEO"->"视频";case "IMAGE"->"商品卡";case "CAROUSEL"->"图文";default->"素材";};
            String product=state.products.stream().filter(p->Objects.equals(p.qcProductId(),material.productId)).map(CreationState.Product::name)
                    .filter(Objects::nonNull).filter(name->!name.isBlank()).findFirst().orElse("商品");
            product=product.replaceAll("[\\p{Cntrl}\\s]+"," ").strip();
            if(product.codePointCount(0,product.length())>20)product=product.substring(0,product.offsetByCodePoints(0,20))+"…";
            String base=type+"｜"+product+"｜"+shortId(Objects.toString(material.id,material.engineMaterialId));
            String name=base;int suffix=2;while(!used.add(name))name=base+"-"+suffix++;
            names.put(material.id,name);
        }
        return names;
    }
    private static String shortId(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,8);}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
