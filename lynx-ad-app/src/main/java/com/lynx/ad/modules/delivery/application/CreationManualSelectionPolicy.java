package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;
import java.util.*;

/** Adding visual media reuses the product's existing copy instead of asking for another title. */
final class CreationManualSelectionPolicy {
    private CreationManualSelectionPolicy() {}
    static List<String> titles(CreationState state,String product,String type,List<String> supplied) {
        if(supplied!=null&&!supplied.isEmpty())return supplied;
        var pool=state.titlePools.get(product);
        if(pool!=null) {
            var titles="IMAGE".equals(type)?pool.cardTitles:pool.customTitles;
            if(!titles.isEmpty())return List.copyOf(titles);
        }
        for(var material:state.materials)if(Objects.equals(product,material.productId)&&Objects.equals(type,material.type)&&!material.titles.isEmpty())return List.copyOf(material.titles);
        String name=state.products.stream().filter(p->p.qcProductId().equals(product)).findFirst().orElseThrow().name();
        String title=DeliveryInputPolicy.text(name);
        if(DeliveryInputPolicy.platformLength(title)<10)title="商品展示："+title;
        // Keep both the platform character count and Java/browser UTF-16 length within their limits.
        while(title.length()>55||DeliveryInputPolicy.platformLength(title)>110)title=title.substring(0,title.offsetByCodePoints(title.length(),-1));
        return List.of(title);
    }
}
