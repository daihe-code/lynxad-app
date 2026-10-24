package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import com.lynx.ad.modules.delivery.domain.model.PlanMetricTemplate;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PlanStatisticsService {
    public record Metrics(BigDecimal cost,BigDecimal gmv,BigDecimal roi,Long orders,
                          BigDecimal netGmv,BigDecimal netRoi,BigDecimal refundRate) {
        public Metrics(BigDecimal cost,BigDecimal gmv,BigDecimal roi,Long orders) {this(cost,gmv,roi,orders,null,null,null);}
    }
    public record Snapshot(Map<String,Metrics> plans,Instant fetchedAt) {}
    private record Key(String tenant,String account,String advertiser,PlanStatisticsRange range,PlanMetricTemplate template) {}
    private final Map<Key,Snapshot> cache=new LinkedHashMap<>();
    private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;
    private final AdManagementPort management;
    public PlanStatisticsService(OauthAccountRepository accounts,QianchuanCredentialFacade credentials,AdManagementPort management) {
        this.accounts=accounts;this.credentials=credentials;this.management=management;
    }
    /** Short-lived account/range cache keeps pagination and exports on the same report. */
    public synchronized Snapshot report(TenantId tenant,String accountId,PlanStatisticsRange range,boolean refresh) {
        return report(tenant,accountId,range,refresh,PlanMetricTemplate.PAYMENT);
    }
    public synchronized Snapshot report(TenantId tenant,String accountId,PlanStatisticsRange range,boolean refresh,PlanMetricTemplate template) {
        var id=new AccountId(accountId);
        var account=accounts.findById(tenant,id).orElseThrow(()->new BizException(1201,"授权账户不存在或不属于当前灵投账户"));
        if(account.platform()!=Platform.QIANCHUAN || !"ACTIVE".equals(account.status())) throw new BizException(1201,"该广告账户授权已失效，无法查询区间统计");
        var credential=credentials.getUsableCredential(tenant,id);
        Key key=new Key(tenant.value(),accountId,credential.advertiserId().value(),range,template);
        Instant now=Instant.now();
        cache.entrySet().removeIf(e->e.getValue().fetchedAt().plusSeconds(60).isBefore(now));
        Snapshot cached=cache.get(key);
        if(!refresh && cached!=null)return cached;
        Map<String,Metrics> values=new HashMap<>();
        var report=template==PlanMetricTemplate.NET?management.netReport(credential,range.startDate(),range.endDate()):null;
        var fields=report==null?Map.<String,String>of():report.metricFields();
        for(JsonNode row:report==null?management.report(credential,range.startDate(),range.endDate()):report.rows()) {
            JsonNode ad=row.path("dimensions").path("ad_id");
            if(ad.isObject())ad=ad.has("Value")?ad.path("Value"):ad.path("value");
            if(ad.isMissingNode() || ad.isNull() || ad.asText().isBlank())throw new BizException(1406,"千川区间报表缺少计划ID，请重试");
            JsonNode m=row.path("metrics");
            BigDecimal cost=QianchuanPlanMapping.number(m,"stat_cost"),gmv=QianchuanPlanMapping.number(m,"total_pay_order_gmv_include_coupon_for_roi2");
            BigDecimal orders=QianchuanPlanMapping.number(m,"total_pay_order_count_for_roi2"),roi=QianchuanPlanMapping.number(m,"total_prepay_and_pay_order_roi2");
            if(roi==null && cost!=null && cost.signum()>0 && gmv!=null)roi=gmv.divide(cost,4,RoundingMode.HALF_UP);
            if(values.putIfAbsent(ad.asText(),new Metrics(cost,gmv,roi,orders==null?null:orders.longValue(),
                    metric(m,fields,"netGmv"),metric(m,fields,"netRoi"),metric(m,fields,"refundRate")))!=null)
                throw new BizException(1406,"千川区间报表包含重复计划，请重试");
        }
        Snapshot result=new Snapshot(Map.copyOf(values),Instant.now());
        if(cache.size()>=256)cache.remove(cache.keySet().iterator().next());
        cache.put(key,result);return result;
    }
    private static BigDecimal metric(JsonNode values,Map<String,String> fields,String key) {
        if(!fields.containsKey(key))return null;
        if("refundRate".equals(key)) {
            JsonNode value=values.path(fields.get(key));
            if(value.isObject())value=value.has("Value")?value.path("Value"):value.path("value");
            String text=value.asText().trim();
            if(text.endsWith("%"))try{return new BigDecimal(text.substring(0,text.length()-1).trim());}catch(NumberFormatException ignored){return null;}
        }
        return QianchuanPlanMapping.number(values,fields.get(key));
    }
}
