package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.catalog.domain.model.Product;
import com.lynx.ad.modules.catalog.domain.port.ProductRepository;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.model.Roi;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.dataops.domain.port.SnapshotPair;
import com.lynx.ad.modules.delivery.application.PlanQueryService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanListPresentationTest {
    private final TenantId tenant = new TenantId("tenant-1");
    private final AwemeId aweme = new AwemeId("aweme-1");
    private final AdPlanRepository plans = mock(AdPlanRepository.class);
    private final MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final AdPlanCreativeBindingStore bindings = mock(AdPlanCreativeBindingStore.class);
    private final OauthAccountRepository accounts = mock(OauthAccountRepository.class);
    private final PlanQueryService service = new PlanQueryService(plans, mock(AdListPort.class), accounts,
            mock(QianchuanCredentialFacade.class), snapshots, products, bindings);

    private AdPlan draft() {
        return AdPlan.draft(tenant, new AccountId("account-1"), aweme, new PlanName("测试计划"),
                new DeliverySetting(SmartBidType.CUSTOM, Money.ofYuan(300),
                        Optional.of(new RoiGoal(new BigDecimal("2.5"))),
                        new Schedule(ScheduleType.FROM_NOW, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
                        AudienceJson.empty()), List.of());
    }

    @Test
    void exposesPersistedProductsAndLatestMetricsWithinTenant() {
        var plan = draft();
        var ts = Instant.parse("2026-10-05T00:00:00Z");
        var createdAt = Instant.now().minusSeconds(3600);
        when(plans.findByTenant(tenant)).thenReturn(List.of(plan));
        when(plans.listMetadataByTenant(tenant)).thenReturn(Map.of(plan.id().value(),
                new AdPlanRepository.ListMetadata(createdAt, "ALL")));
        when(accounts.findByTenant(tenant)).thenReturn(List.of());
        when(bindings.productIdsByPlan(List.of(plan.id().value())))
                .thenReturn(Map.of(plan.id().value(), List.of("product-1", "missing-product")));
        when(products.findByAweme(tenant, aweme)).thenReturn(List.of(Product.fromPool(tenant, aweme,
                new QcProductId("product-1"), "商品一", Money.ofYuan(50), "https://example.com/product.png")));
        when(snapshots.listLatestPairsByTenant(tenant)).thenReturn(Map.of(plan.id().value(), new SnapshotPair(MetricSnapshot.of(tenant, plan.id(),
                new QcPlanId("qc-1"), ts, 1000, 100, Money.ofYuan(20), 3, Money.ofYuan(60),
                new Roi(new BigDecimal("3")), null), null)));

        var row = service.list(tenant, 1, 10).records().getFirst();
        assertEquals(0, new BigDecimal("300").compareTo(row.budget()));
        assertEquals(new BigDecimal("2.5"), row.roi2Goal());
        assertEquals(LocalDate.of(2026, 10, 31), row.endDate());
        assertEquals(3L, row.convert());
        assertEquals(ts, row.snapshotTs());
        assertEquals(createdAt, row.createdAt());
        assertEquals("ALL", row.deliveryPlatform());
        assertEquals("商品一", row.products().getFirst().name());
        assertEquals("missing-product", row.products().get(1).id());
        assertNull(row.products().get(1).imageUrl());
        verify(products).findByAweme(tenant, aweme);
    }

    @Test
    void missingSnapshotIsUnknownInsteadOfZeroAndDoesNotLoadCatalog() {
        var plan = draft();
        when(plans.findByTenant(tenant)).thenReturn(List.of(plan));
        when(accounts.findByTenant(tenant)).thenReturn(List.of());
        when(snapshots.listLatestPairsByTenant(tenant)).thenReturn(Map.of());
        when(bindings.productIdsByPlan(List.of(plan.id().value()))).thenReturn(Map.of());

        var row = service.list(tenant, 1, 10).records().getFirst();
        assertNull(row.costYuan());
        assertNull(row.gmvYuan());
        assertNull(row.convert());
        assertNull(row.roi());
        assertNull(row.snapshotTs());
        assertNull(row.createdAt());
        assertNull(row.deliveryPlatform());
        assertTrue(row.products().isEmpty());
        assertTrue(service.list(tenant, 2, 10).records().isEmpty());
        verifyNoInteractions(products);
    }

    @Test
    void expiredDraftDoesNotAppearEvenBeforeCleanupFinishesAndCannotOccupyPagination() {
        var plan=draft();
        when(plans.findByTenant(tenant)).thenReturn(List.of(plan));
        when(plans.listMetadataByTenant(tenant)).thenReturn(Map.of(plan.id().value(),
                new AdPlanRepository.ListMetadata(Instant.now().minusSeconds(604801),"ALL")));
        var result=service.list(tenant,1,10);
        assertEquals(0,result.total());assertTrue(result.records().isEmpty());
        when(plans.listMetadataByTenant(tenant)).thenReturn(Map.of(plan.id().value(),
                new AdPlanRepository.ListMetadata(Instant.now().minusSeconds(3600),"ALL")));
        assertEquals(1,service.list(tenant,1,10).total());
    }

    @Test
    void comparesLatestSnapshotsAndDoesNotDivideByZero() {
        var plan = draft();
        var currentTs = Instant.parse("2026-10-05T08:00:00Z");
        var previousTs = currentTs.minusSeconds(300);
        var current = MetricSnapshot.of(tenant, plan.id(), null, currentTs, 0, 0,
                Money.ofYuan(250), 108, Money.ofYuan(1427.5), new Roi(new BigDecimal("5.71")), null);
        var previous = MetricSnapshot.of(tenant, plan.id(), null, previousTs, 0, 0,
                Money.ofYuan(200), 100, Money.ofYuan(1042), new Roi(new BigDecimal("5.21")), null);
        when(plans.findByTenant(tenant)).thenReturn(List.of(plan));
        when(accounts.findByTenant(tenant)).thenReturn(List.of());
        when(bindings.productIdsByPlan(List.of(plan.id().value()))).thenReturn(Map.of());
        when(snapshots.listLatestPairsByTenant(tenant)).thenReturn(Map.of(plan.id().value(), new SnapshotPair(current, previous)));
        var comparison = service.list(tenant, 1, 10).records().getFirst().comparison();
        assertEquals(new BigDecimal("25.00"), comparison.costChangePct());
        assertEquals(new BigDecimal("8.00"), comparison.convertChangePct());
        assertEquals(new BigDecimal("0.50"), comparison.roiChange());
        assertEquals(new BigDecimal("9.60"), comparison.roiChangePct());
        assertEquals(previousTs, comparison.previousSnapshotTs());

        var zero = MetricSnapshot.of(tenant, plan.id(), null, previousTs, 0, 0,
                Money.ofYuan(0), 0, Money.ofYuan(0), null, null);
        when(snapshots.listLatestPairsByTenant(tenant)).thenReturn(Map.of(plan.id().value(), new SnapshotPair(current, zero)));
        comparison = service.list(tenant, 1, 10).records().getFirst().comparison();
        assertNull(comparison.costChangePct());
        assertNull(comparison.convertChangePct());
        assertNull(comparison.roiChange());
        assertNull(comparison.roiChangePct());

        var zeroRoi = MetricSnapshot.of(tenant, plan.id(), null, previousTs, 0, 0,
                Money.ofYuan(200), 100, Money.ofYuan(0), new Roi(BigDecimal.ZERO), null);
        when(snapshots.listLatestPairsByTenant(tenant)).thenReturn(Map.of(plan.id().value(), new SnapshotPair(current, zeroRoi)));
        assertNull(service.list(tenant, 1, 10).records().getFirst().comparison().roiChangePct());
    }
}
