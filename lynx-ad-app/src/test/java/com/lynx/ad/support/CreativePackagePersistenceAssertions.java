package com.lynx.ad.support;

import static org.junit.jupiter.api.Assertions.*;
import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared persistence contract, independent of any container lifecycle. */
public final class CreativePackagePersistenceAssertions {
    private CreativePackagePersistenceAssertions() {}
    public static void verifyConfirmationRoundTrip(JdbcTemplate jdbc,CreativeTaskRepository packages) {
        String id=UUID.randomUUID().toString(),tenant="package-persistence-fixture";
        jdbc.update("INSERT INTO creative_package(id,tenant_id,task_id,version,artifacts_json,created_at) VALUES(?,?,?,1,'[{\"productId\":\"p1\",\"titles\":[{\"text\":\"隔离测试\",\"type\":\"CUSTOM\"}],\"videoId\":\"fixture-video\"}]',CURRENT_TIMESTAMP)",id,tenant,UUID.randomUUID().toString());
        var key=new PackageId(id);var owner=new TenantId(tenant);
        var initial=packages.findPackage(owner,key).orElseThrow();
        assertFalse(initial.isConfirmed());
        initial.confirm();
        assertDoesNotThrow(()->packages.savePackage(initial));
        var confirmed=packages.findPackage(owner,key).orElseThrow();
        assertTrue(confirmed.isConfirmed());assertEquals(2,confirmed.version());
        assertEquals(initial.createdAt(),confirmed.createdAt());
        assertTrue(packages.findPackage(new TenantId("another-tenant"),key).isEmpty());
        confirmed.confirm();packages.savePackage(confirmed);
        var repeated=packages.findPackage(owner,key).orElseThrow();
        assertEquals(2,repeated.version(),"repeated confirmation is idempotent");
        assertEquals(confirmed.confirmedAt(),repeated.confirmedAt(),"repeated confirmation preserves the persisted timestamp");
        packages.savePackage(new CreativePackage(key,3,null,confirmed.items(),confirmed.createdAt()));
        var cleared=packages.findPackage(owner,key).orElseThrow();
        assertFalse(cleared.isConfirmed(),"null confirmation must persist after content changes");
        assertEquals(3,cleared.version());
    }
}
