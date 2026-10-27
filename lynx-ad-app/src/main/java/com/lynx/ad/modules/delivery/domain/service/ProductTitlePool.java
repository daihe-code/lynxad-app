package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.common.exception.BizException;
import java.util.*;

/** Explicit pools are authoritative, including provenance after manual edits. */
public final class ProductTitlePool {
    private ProductTitlePool() {}
    public static CreationState.TitlePool of(CreationState s,String product) {
        var saved=s.titlePools.get(product);
        var pool=new CreationState.TitlePool();
        for(var m:s.activeMaterials())if(product.equals(m.productId)) {
            boolean card="IMAGE".equals(m.type);var titles=values(pool,card);var generated=generated(pool,card);
            for(String title:m.titles){if(!titles.contains(title))titles.add(title);if(m.engineMaterialId!=null&&!m.engineMaterialId.isBlank()&&!generated.contains(title))generated.add(title);}
        }
        if(saved!=null){
            // A previously absent media type can be added after the other pool was edited.
            if(saved.customTitles.isEmpty()&&saved.customTextAssetId==null){saved.customTitles=pool.customTitles;saved.customGeneratedTitles=pool.customGeneratedTitles;}
            if(saved.cardTitles.isEmpty()&&saved.cardTextAssetId==null){saved.cardTitles=pool.cardTitles;saved.cardGeneratedTitles=pool.cardGeneratedTitles;}
            if(!saved.provenanceRecorded){saved.customGeneratedTitles=new ArrayList<>(pool.customGeneratedTitles.stream().filter(saved.customTitles::contains).toList());saved.cardGeneratedTitles=new ArrayList<>(pool.cardGeneratedTitles.stream().filter(saved.cardTitles::contains).toList());}
            pool=saved;
        }
        pool.provenanceRecorded=true;return pool;
    }
    public static List<String> values(CreationState.TitlePool p,boolean card){return card?p.cardTitles:p.customTitles;}
    public static List<String> generated(CreationState.TitlePool p,boolean card){return card?p.cardGeneratedTitles:p.customGeneratedTitles;}
    public static void validate(List<String> titles,boolean card) {
        DeliveryInputPolicy.titles(titles,true);
        if(card&&titles.stream().anyMatch(t->t.length()>30))throw new BizException(1503,"商品卡标题最多30字");
        if(new HashSet<>(titles).size()!=titles.size())throw new BizException(1503,"同一标题集合不能包含重复文案");
    }
}
