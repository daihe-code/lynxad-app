package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.service.CreationDraftSchedule;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class CreationDraftScheduleTest {
    final LocalDate today=LocalDate.of(2026,10,31);
    CreationState draft(String start,String end) {
        var s=new CreationState();s.tenantId="tenant";s.planId="plan";s.step=3;s.scheduleType="DATE_RANGE";s.startDate=start;s.endDate=end;
        return s;
    }
    @Test void expiredStartMovesToTodayWhileFutureEndStaysUnchanged() {
        var s=draft("2026-10-29","2026-11-11");var corrected=CreationDraftSchedule.correction(s,today);
        assertEquals(today,corrected.startDate());assertEquals(LocalDate.of(2026,11,11),corrected.endDate());
        assertEquals("2026-10-29",s.startDate);
    }
    @Test void fullyExpiredRangeKeepsItsOriginalNumberOfDeliveryDaysIncludingSingleDay() {
        assertEquals(LocalDate.of(2026,11,3),CreationDraftSchedule.correction(draft("2026-10-20","2026-10-23"),today).endDate());
        assertEquals(today,CreationDraftSchedule.correction(draft("2026-10-29","2026-10-29"),today).endDate());
    }
    @Test void futureAndMalformedRangesAreNotSilentlyReplaced() {
        for(var dates:List.of(List.of("2026-10-31","2026-11-11"),List.of("2026-11-01","2026-11-11"),
                List.of("2026-02-30","2026-11-11"),List.of("2026-10-29","2026-10-28"),List.of("2026-10-29","")))
            assertNull(CreationDraftSchedule.correction(draft(dates.getFirst(),dates.getLast()),today));
    }
    @Test void publishedAndUncertainPlansAndPublishedWorkingCopiesKeepTheirHistoricalDates() {
        var s=draft("2026-10-20","2026-10-23");
        for(String status:List.of("QUEUED","SENDING","UNCERTAIN","CREATED")){s.publishStatus=status;assertNull(CreationDraftSchedule.correction(s,today));}
        s.publishStatus="NONE";s.qcPlanId="platform-plan";assertNull(CreationDraftSchedule.correction(s,today));
        s.qcPlanId=null;s.publishedEdit=new CreationState.PublishedEdit();assertNull(CreationDraftSchedule.correction(s,today));
        s.publishedEdit=null;s.scheduleType="FROM_NOW";assertNull(CreationDraftSchedule.correction(s,today));
    }
    @Test void reopeningDraftPersistsTheSameDatesInCreationAndPlanWithoutChangingMaterialProgress() {
        var now=LocalDate.now(ZoneId.of("Asia/Shanghai"));var s=draft(now.minusDays(2).toString(),now.plusDays(5).toString());
        s.revision=7;s.reachedStep=3;s.confirmedVersion=s.packageVersion;
        var store=mock(CreationStore.class);var plans=mock(AdPlanRepository.class);var p=mock(AdPlan.class);
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(eq("tenant"),eq("plan"),eq(7),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(plans.findById(new TenantId("tenant"),new PlanId("plan"))).thenReturn(Optional.of(p));
        when(p.qcPlanId()).thenReturn(Optional.empty());when(p.status()).thenReturn(PlanStatus.DRAFT);
        var setting=new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(new java.math.BigDecimal("50")),Optional.empty(),Schedule.fromNow(),AudienceJson.empty());
        when(p.setting()).thenReturn(setting);
        var service=new CreationWorkflowService(store,plans,null,null,null,new ObjectMapper(),null,null,null,null,
                mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
        service.get(new TenantId("tenant"),"plan");service.get(new TenantId("tenant"),"plan");
        assertEquals(now.toString(),s.startDate);assertEquals(now.plusDays(5).toString(),s.endDate);
        assertEquals(8,s.revision);assertEquals(3,s.step);assertEquals(3,s.reachedStep);assertEquals(1,s.packageVersion);assertNull(s.confirmedVersion);
        var saved=ArgumentCaptor.forClass(DeliverySetting.class);verify(p).updateSetting(saved.capture());
        assertEquals(setting.dailyBudget(),saved.getValue().dailyBudget());assertEquals(now,saved.getValue().schedule().startDate());
        verify(plans,times(1)).save(p);
    }
}
