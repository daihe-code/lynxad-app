package com.lynx.ad.modules.delivery.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CampaignTrendServiceTest {
    final TenantId tenant=new TenantId("tenant-a");
    final LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(2);
    final PlanStatisticsRange range=new PlanStatisticsRange(day,day);
    final PlanQueryService queries=mock(PlanQueryService.class);final PlanStatisticsService statistics=mock(PlanStatisticsService.class);
    final CampaignTrendService service=new CampaignTrendService(queries,statistics);
    PlanQueryService.Filters filter(PlanStatisticsRange value,boolean refresh) {return new PlanQueryService.Filters("选中的商品",null,"ALL",null,null,null,null,true,value,refresh,PlanMetricTemplate.NET);}
    PlanStatisticsService.Metrics m(String cost,String gmv,String net,String rate) {
        return new PlanStatisticsService.Metrics(new BigDecimal(cost),new BigDecimal(gmv),null,2L,net==null?null:new BigDecimal(net),null,rate==null?null:new BigDecimal(rate));
    }
    PlanStatisticsService.TrendPoint p(String id,int hour,PlanStatisticsService.Metrics m) {return new PlanStatisticsService.TrendPoint(id,day+String.format(" %02d:00:00",hour),m);}
    PlanStatisticsService.TrendSnapshot snapshot(String dimension,PlanStatisticsService.TrendPoint... points) {return new PlanStatisticsService.TrendSnapshot(List.of(points),dimension,Instant.now(),null);}
    @Test void sumsOnlyFilteredPlansBeforePaginationAndUsesWeightedFinancialMetrics() {
        var f=filter(range,false);when(queries.statisticsScope(tenant,f)).thenReturn(Map.of("account-a",Set.of("1"),"account-b",Set.of("2")));
        var zero=new PlanStatisticsService.Metrics(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0L,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        when(statistics.trend(tenant,"account-a",range,PlanMetricTemplate.NET,false,false)).thenReturn(snapshot("stat_time_hour",p("1",9,m("10","100","90","10")),p("foreign",9,m("9999","99999","99999","99")),p("1",10,zero)));
        when(statistics.trend(tenant,"account-b",range,PlanMetricTemplate.NET,false,false)).thenReturn(snapshot("stat_time_hour",p("2",9,m("90","900","720","20")),p("2",10,zero)));
        var result=service.data(tenant,f);assertEquals(24,result.points().size());assertEquals("stat_time_hour",result.timeDimension());
        var hour=result.points().get(9).metrics();assertEquals(new BigDecimal("100"),hour.get("cost"));assertEquals(new BigDecimal("810"),hour.get("netGmv"));
        assertEquals(new BigDecimal("8.1000"),hour.get("netRoi"));assertEquals(new BigDecimal("19.0000"),hour.get("refundRate"));
        assertEquals(BigDecimal.ZERO,result.points().get(8).metrics().get("cost"));assertEquals(BigDecimal.ZERO,result.points().get(10).metrics().get("cost"));
        assertEquals(BigDecimal.ZERO,result.points().get(10).metrics().get("netRoi"));assertEquals(BigDecimal.ZERO,result.points().get(10).metrics().get("refundRate"));
        verify(statistics,times(2)).trend(any(),any(),any(),any(),eq(false),eq(false));verify(queries).statisticsScope(tenant,f);
    }
    @Test void differentAccountTimeCapabilitiesAreRequeriedAtACommonDailyGranularity() {
        var f=filter(range,true);when(queries.statisticsScope(tenant,f)).thenReturn(Map.of("a",Set.of("1"),"b",Set.of("2")));
        when(statistics.trend(tenant,"a",range,PlanMetricTemplate.NET,false,true)).thenReturn(snapshot("stat_time_hour",p("1",9,m("10","100","90","10"))));
        when(statistics.trend(tenant,"b",range,PlanMetricTemplate.NET,false,true)).thenReturn(snapshot("stat_time_day",new PlanStatisticsService.TrendPoint("2",day.toString(),m("90","900","720","20"))));
        when(statistics.trend(tenant,"a",range,PlanMetricTemplate.NET,true,true)).thenReturn(snapshot("stat_time_day",new PlanStatisticsService.TrendPoint("1",day.toString(),m("20","200","180","10"))));
        var result=service.data(tenant,f);assertEquals("stat_time_day",result.timeDimension());assertEquals(1,result.points().size());
        assertEquals(new BigDecimal("110"),result.points().getFirst().metrics().get("cost"));verify(statistics).trend(tenant,"a",range,PlanMetricTemplate.NET,true,true);
    }
    @Test void inactiveDatesAreZeroButMissingNetFieldsCannotUsePaymentFields() {
        var dates=new PlanStatisticsRange(day.minusDays(2),day);var f=filter(dates,false);
        when(queries.statisticsScope(tenant,f)).thenReturn(Map.of("a",Set.of("1")));
        when(statistics.trend(tenant,"a",dates,PlanMetricTemplate.NET,true,false)).thenReturn(snapshot("stat_time_day",new PlanStatisticsService.TrendPoint("1",day.toString(),m("10","100",null,null))));
        var result=service.data(tenant,f);assertEquals(3,result.points().size());assertEquals(BigDecimal.ZERO,result.points().get(1).metrics().get("cost"));
        assertNull(result.points().get(2).metrics().get("netGmv"));assertNull(result.points().get(2).metrics().get("netRoi"));assertNull(result.points().get(2).metrics().get("refundRate"));
    }
    @Test void completeEmptyTrendUsesCapabilityMetadataWhileUnsupportedMetricsRemainUnknown() {
        var dates=new PlanStatisticsRange(day.minusDays(2),day);var f=filter(dates,false);
        when(queries.statisticsScope(tenant,f)).thenReturn(Map.of("a",Set.of("1")));
        when(statistics.trend(tenant,"a",dates,PlanMetricTemplate.NET,true,false)).thenReturn(new PlanStatisticsService.TrendSnapshot(
                List.of(),"stat_time_day",Instant.now(),null,Set.of("cost","gmv","orders","netGmv","netRoi")));
        var result=service.data(tenant,f);assertEquals(3,result.points().size());
        for(var point:result.points()) {assertEquals(BigDecimal.ZERO,point.metrics().get("cost"));assertEquals(BigDecimal.ZERO,point.metrics().get("netGmv"));assertEquals(BigDecimal.ZERO,point.metrics().get("netRoi"));assertNull(point.metrics().get("refundRate"));}
    }
    @Test void unavailableTimeDimensionDoesNotBecomeAFlatZeroLine() {
        var f=filter(range,false);when(queries.statisticsScope(tenant,f)).thenReturn(Map.of("a",Set.of("1")));
        when(statistics.trend(tenant,"a",range,PlanMetricTemplate.NET,false,false)).thenReturn(new PlanStatisticsService.TrendSnapshot(
                List.of(),null,Instant.now(),"平台未提供时间维度",Set.of("cost")));
        var result=service.data(tenant,f);assertTrue(result.points().stream().allMatch(p->p.metrics().get("cost")==null));assertNotNull(result.warning());
    }
    @Test void anAccountFailureDoesNotReturnAnotherAccountsPartialTrendAsTheWholeScope() {
        var f=filter(range,false);when(queries.statisticsScope(tenant,f)).thenReturn(Map.of("a",Set.of("1")));
        when(statistics.trend(tenant,"a",range,PlanMetricTemplate.NET,false,false)).thenThrow(new BizException(1201,"授权失效"));
        assertThrows(BizException.class,()->service.data(tenant,f));
    }
    @Test void emptyScopeDoesNotCallPlatformOrCreateZeroValues() {
        var f=filter(range,false);when(queries.statisticsScope(tenant,f)).thenReturn(Map.of());
        var result=service.data(tenant,f);assertTrue(result.points().stream().allMatch(p->p.metrics().values().stream().allMatch(Objects::isNull)));verifyNoInteractions(statistics);
    }
}
