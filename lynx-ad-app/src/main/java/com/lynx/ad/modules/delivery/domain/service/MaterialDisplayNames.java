package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.*;

/** Management labels are independent of the advertising titles used by previews and publication. */
public final class MaterialDisplayNames {
    private MaterialDisplayNames() {}

    public static Map<String,String> of(CreationState state) {
        var names=new LinkedHashMap<String,String>();
        for(var material:state.materials) {
            if(material.engineMaterialId==null||material.engineMaterialId.isBlank()) {
                names.put(material.id,material.title);continue;
            }
            String product=state.products.stream().filter(p->Objects.equals(p.qcProductId(),material.productId)).map(CreationState.Product::name)
                    .filter(Objects::nonNull).filter(name->!name.isBlank()).findFirst().orElse("商品");
            product=product.replaceAll("[\\p{Cntrl}\\s]+"," ").strip();
            if(product.codePointCount(0,product.length())>20)product=product.substring(0,product.offsetByCodePoints(0,20))+"…";
            names.put(material.id,product);
        }
        return names;
    }
}
