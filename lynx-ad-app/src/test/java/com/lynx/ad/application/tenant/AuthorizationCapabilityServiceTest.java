package com.lynx.ad.application.tenant;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.application.AuthorizationCapabilityService;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationCapabilityServiceTest {
    JdbcTemplate jdbc;OauthAccount account=mock(OauthAccount.class);OauthAccountRepository accounts=mock(OauthAccountRepository.class);AuthorizationCapabilityService service;
    TenantId tenant=new TenantId("tenant");Instant authorized=Instant.now().minusSeconds(3600);
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:capability"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE creative_asset_platform(tenant_id VARCHAR(36),account_id VARCHAR(36),status VARCHAR(24),error_message VARCHAR(512),updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE oauth_capability_check(id VARCHAR(36),tenant_id VARCHAR(36),account_id VARCHAR(36),grant_id VARCHAR(36),account_status VARCHAR(24),material_status VARCHAR(24),source VARCHAR(64),checked_at TIMESTAMP)");
        when(accounts.findById(tenant,new AccountId("account"))).thenReturn(Optional.of(account));when(account.status()).thenReturn("ACTIVE");when(account.awemes()).thenReturn(List.of(mock(Aweme.class)));when(account.authorizedAt()).thenReturn(authorized);when(account.grantId()).thenReturn("grant");
        service=new AuthorizationCapabilityService(jdbc,accounts);
    }
    void failure(String tenantId,String message,Instant at){jdbc.update("INSERT INTO creative_asset_platform VALUES(?,?,?,?,?)",tenantId,"account","FAILED",message,Timestamp.from(at));}
    @Test void validAccountDoesNotProveFullMaterialPermission(){
        var check=service.record(tenant,"account");assertEquals("AVAILABLE",check.accountStatus());assertEquals("UNKNOWN",check.materialStatus());assertEquals("UNKNOWN",check.status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_capability_check WHERE tenant_id='tenant' AND grant_id='grant'",Integer.class));
    }
    @Test void onlyCurrentTenantAndCurrentAuthorizationEvidenceCanBlockMaterials(){
        failure("other","未授权敏感物料",Instant.now());failure("tenant","未授权敏感物料",authorized.minusSeconds(1));failure("tenant","文件格式错误",Instant.now());
        assertEquals("UNKNOWN",service.inspect(tenant,"account").materialStatus());
        failure("tenant","平台拒绝：未勾选敏感物料授权",Instant.now());
        assertEquals("MISSING",service.inspect(tenant,"account").materialStatus());assertThrows(BizException.class,()->service.requireMaterials(tenant,"account"));
        when(account.authorizedAt()).thenReturn(Instant.now().plusSeconds(10));assertEquals("UNKNOWN",service.inspect(tenant,"account").materialStatus());
    }
    @Test void foreignAccountCannotBeInspected(){assertThrows(BizException.class,()->service.inspect(new TenantId("other"),"account"));}
    @Test void unboundAccountCannotStartNewMaterialWork(){
        when(account.status()).thenReturn("UNBOUND");
        assertEquals("UNAVAILABLE",service.inspect(tenant,"account").accountStatus());
        assertThrows(BizException.class,()->service.requireMaterials(tenant,"account"));
        when(account.status()).thenReturn("ACTIVE");when(account.awemes()).thenReturn(List.of());
        assertThrows(BizException.class,()->service.requireMaterials(tenant,"account"));
    }
}
