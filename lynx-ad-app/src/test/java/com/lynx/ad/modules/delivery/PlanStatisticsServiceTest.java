package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.application.PlanStatisticsService;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class PlanStatisticsServiceTest {
    final ObjectMapper json=new ObjectMapper();final TenantId tenant=new TenantId("tenant-a");final AccountId account=new AccountId("account-a");
    final OauthAccountRepository accounts=mock(OauthAccountRepository.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);final AdManagementPort port=mock(AdManagementPort.class);
    final PlanStatisticsService service=new PlanStatisticsService(accounts,credentials,port);
    final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    final PlanStatisticsRange range=new PlanStatisticsRange(today.minusDays(6),today);
    final Credential credential=new Credential("test-token",new AdvertiserId("123"));
    @BeforeEach void setup() {
        when(accounts.findById(tenant,account)).thenReturn(Optional.of(new OauthAccount(account,tenant,Platform.QIANCHUAN,new AdvertiserId("123"),"授权账户",null,null,Instant.now().plusSeconds(3600),null,"ACTIVE",List.of())));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);
    }
    @Test void supportsSingleDayAndExactly180InclusiveDays() {
        assertEquals(today,new PlanStatisticsRange(today,today).startDate());assertNotNull(new PlanStatisticsRange(today.minusDays(179),today));
        assertNull(PlanStatisticsRange.optional(null,null));
    }
    @Test void rejects181DaysReversedFutureAndIncompleteDates() {
        assertThrows(BizException.class,()->new PlanStatisticsRange(today.minusDays(180),today));
        assertThrows(BizException.class,()->new PlanStatisticsRange(today,today.minusDays(1)));
        assertThrows(BizException.class,()->new PlanStatisticsRange(today,today.plusDays(1)));
        assertThrows(BizException.class,()->PlanStatisticsRange.optional(today.toString(),null));
        assertThrows(BizException.class,()->PlanStatisticsRange.optional("2026-02-30",today.toString()));
        assertThrows(BizException.class,()->PlanStatisticsRange.optional("invalid",today.toString()));
    }
    @Test void cachesOnlyTheSameTenantAccountAndRangeAndRefreshBypassesCache() throws Exception {
        when(port.report(credential,range.startDate(),range.endDate())).thenReturn(List.of(json.readTree("{\"dimensions\":{\"ad_id\":{\"Value\":9007199254740993}},\"metrics\":{\"stat_cost\":20,\"total_pay_order_gmv_include_coupon_for_roi2\":100,\"total_pay_order_count_for_roi2\":2}}")));
        var first=service.report(tenant,account.value(),range,false);assertEquals(new BigDecimal("5.0000"),first.plans().get("9007199254740993").roi());
        assertEquals(2L,first.plans().get("9007199254740993").orders());assertSame(first,service.report(tenant,account.value(),range,false));
        verify(port).report(credential,range.startDate(),range.endDate());service.report(tenant,account.value(),range,true);
        verify(port,times(2)).report(credential,range.startDate(),range.endDate());
        var other=new PlanStatisticsRange(today,today);service.report(tenant,account.value(),other,false);verify(port).report(credential,today,today);
    }
    @Test void absentReportsAndMissingMetricsStayUnknownWhileActualZeroIsPreserved() throws Exception {
        when(port.report(credential,range.startDate(),range.endDate())).thenReturn(List.of(json.readTree("{\"dimensions\":{\"ad_id\":{\"value\":123}},\"metrics\":{\"stat_cost\":{\"Value\":0}}}")));
        var values=service.report(tenant,account.value(),range,false).plans();assertEquals(BigDecimal.ZERO,values.get("123").cost());
        assertNull(values.get("123").orders());assertNull(values.get("123").gmv());assertNull(values.get("123").roi());assertNull(values.get("456"));
    }
    @Test void tenantCannotReadAnotherTenantsCachedReports() {
        service.report(tenant,account.value(),range,false);
        assertThrows(BizException.class,()->service.report(new TenantId("tenant-b"),account.value(),range,false));
        verify(port,times(1)).report(any(),any(),any());
    }
    @Test void cachedReportsStillRequireUsableAuthorization() {
        service.report(tenant,account.value(),range,false);
        when(credentials.getUsableCredential(tenant,account)).thenThrow(new BizException(1211,"授权失效"));
        assertThrows(BizException.class,()->service.report(tenant,account.value(),range,false));verify(port,times(1)).report(any(),any(),any());
    }
    @Test void failedRangeQueryIsNotCachedOrReplacedWithAnotherRangesResults() {
        when(port.report(credential,range.startDate(),range.endDate())).thenThrow(new BizException(1406,"查询失败"));
        assertThrows(BizException.class,()->service.report(tenant,account.value(),range,false));assertThrows(BizException.class,()->service.report(tenant,account.value(),range,false));
        verify(port,times(2)).report(credential,range.startDate(),range.endDate());
    }
}
