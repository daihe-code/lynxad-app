package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Explainable relevance from observed product facts; never predicts sales or ROI. */
public final class ProductRecommendationPolicy {
    private ProductRecommendationPolicy() {}
    public record Suggestion(Product product,List<String> reasons,List<String> matchedProductIds) {}
    private record Match(Product seed,int score,List<String> reasons) {}
    private record Ranked(Suggestion suggestion,int score) {}
    private static final Set<String> BROAD=Set.of("其他","其它","未分类","未知","通用","商品","运动户外","服饰","食品","日用品");
    private static final List<String> NOISE=List.of("官方旗舰店","高品质","男女通用","学生款","新品上市","商品","产品","男女","学生","新款","同款","正品","包邮","促销","旗舰店","直播","专用","适用","适合","家用","便携","轻便","收纳","户外","成人","儿童","多功能","升级","套装");

    public static List<Suggestion> recommend(List<Product> seeds,List<Product> candidates,int limit) {
        var selected=new HashSet<String>();seeds.forEach(p->selected.add(p.qcProductId()));
        var seen=new HashSet<String>();var ranked=new ArrayList<Ranked>();
        for(var candidate:candidates) {
            if(!eligible(candidate)||selected.contains(candidate.qcProductId())||!seen.add(candidate.qcProductId()))continue;
            var matches=seeds.stream().map(seed->match(seed,candidate)).filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(Match::score).reversed().thenComparing(m->m.seed().qcProductId())).toList();
            if(matches.isEmpty())continue;
            var best=matches.getFirst();
            ranked.add(new Ranked(new Suggestion(candidate,best.reasons(),matches.stream().map(m->m.seed().qcProductId()).toList()),best.score()+Math.min(6,matches.size()-1)));
        }
        return ranked.stream().sorted(Comparator.comparingInt(Ranked::score).reversed().thenComparing(r->r.suggestion().product().qcProductId()))
                .limit(Math.max(0,Math.min(6,limit))).map(Ranked::suggestion).toList();
    }
    public static boolean eligible(Product p) {
        return p!=null&&p.qcProductId()!=null&&p.qcProductId().matches("[1-9][0-9]{0,18}")
                &&p.name()!=null&&!p.name().isBlank()&&p.draft()==null
                &&(p.stock()==null||p.stock()>0)&&(p.unavailableReasons()==null||p.unavailableReasons().isEmpty());
    }
    private static Match match(Product seed,Product candidate) {
        String category=leaf(seed.category());
        boolean sameCategory=!category.isBlank()&&!BROAD.contains(category)&&category.equals(leaf(candidate.category()));
        var a=terms(seed.name());var b=terms(candidate.name());var common=new TreeSet<String>(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        common.addAll(a);common.retainAll(b);
        boolean keywordMatch=common.size()>=2&&common.size()/(double)Math.max(1,Math.min(a.size(),b.size()))>=.2;
        if(!sameCategory&&!keywordMatch)return null;
        var reasons=new ArrayList<String>();int score=0;
        if(sameCategory){score+=50;reasons.add("与「"+shortName(seed.name())+"」同属「"+shortName(seed.category())+"」");}
        if(keywordMatch){score+=Math.min(35,common.size()*5);var words=new ArrayList<String>();for(String word:common){if(words.stream().noneMatch(existing->existing.contains(word)))words.add(word);if(words.size()==3)break;}reasons.add("名称共有关键词："+String.join("、",words));}
        var sp=price(seed);var cp=price(candidate);
        if(sp!=null&&cp!=null){double ratio=cp.doubleValue()/sp.doubleValue();if(ratio>=.7&&ratio<=1.4){score+=15;reasons.add("参考价格处于相近价格带");}}
        return new Match(seed,score,List.copyOf(reasons));
    }
    private static BigDecimal price(Product p){var value=p.userPrice()!=null?p.userPrice():p.price();return value!=null&&value.signum()>0&&!(p.priceInfo()!=null&&p.priceInfo().stale()&&p.userPrice()==null)?value:null;}
    private static String leaf(String value){String text=normalize(value);return text.isBlank()?"":leafPart(text);}
    private static String leafPart(String text){String[] parts=text.split("[>/\\\\|→]+");return parts.length==0?"":parts[parts.length-1].strip();}
    private static String normalize(String value){return value==null?"":Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();}
    private static String shortName(String value){String text=value==null?"":value.strip();int n=text.codePointCount(0,text.length());return n>24?text.substring(0,text.offsetByCodePoints(0,24))+"…":text;}
    private static Set<String> terms(String name) {
        String text=normalize(name);for(String noise:NOISE)text=text.replace(noise," ");
        var result=new HashSet<String>();var matcher=java.util.regex.Pattern.compile("[\\p{IsHan}]+|[a-z]{3,}").matcher(text);
        while(matcher.find()){String word=matcher.group();if(word.charAt(0)>127){for(int n=2;n<=3;n++)for(int i=0;i+n<=word.length();i++){String token=word.substring(i,i+n);if(!BROAD.contains(token))result.add(token);}}else result.add(word);}
        return result;
    }
}
