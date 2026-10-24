package com.lynx.ad.modules.delivery.application;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CreationManualSelectionPolicyTest {
    CreationState state(String name){var s=new CreationState();s.products.add(new CreationState.Product("p","p",name,null,null,null,null,null,List.of(),List.of()));return s;}
    @Test void reusesProductCopyWithoutMixingOtherProductsOrFormats(){
        var s=state("户外登山双肩背包");var pool=new CreationState.TitlePool();pool.customTitles.add("户外登山背包展示");pool.cardTitles.add("轻便背包商品卡展示");s.titlePools.put("p",pool);
        assertEquals(pool.customTitles,CreationManualSelectionPolicy.titles(s,"p","VIDEO",null));assertEquals(pool.cardTitles,CreationManualSelectionPolicy.titles(s,"p","IMAGE",List.of()));
        assertEquals(List.of("用户已填写的有效标题"),CreationManualSelectionPolicy.titles(s,"p","VIDEO",List.of("用户已填写的有效标题")));
    }
    @Test void fallbackIsProductNameAndRemainsValidForShortLongAndUnicodeNames(){
        for(String name:List.of("枕头","真实商品名称".repeat(30),"🚲".repeat(80))){var titles=CreationManualSelectionPolicy.titles(state(name),"p","VIDEO",null);assertDoesNotThrow(()->DeliveryInputPolicy.titles(titles,true));assertFalse(Character.isHighSurrogate(titles.getFirst().charAt(titles.getFirst().length()-1)));}
    }
}
