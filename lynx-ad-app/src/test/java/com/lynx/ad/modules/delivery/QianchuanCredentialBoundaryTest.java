package com.lynx.ad.modules.delivery;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
class QianchuanCredentialBoundaryTest {
    private final TenantId tenant=new TenantId("tenant-a");private final AccountId id=new AccountId("account-a");
    private final OauthAccountRepository repository=mock(OauthAccountRepository.class);
    private final Encryptor encryptor=mock(Encryptor.class);
    private final QianchuanCredentialFacade facade=new QianchuanCredentialFacade(repository,encryptor,new LynxAdProperties());
    private OauthAccount account(Instant expires,String status) { return new OauthAccount(id,tenant,Platform.QIANCHUAN,new AdvertiserId("123"),"授权账户",new EncryptedToken("encrypted"),null,expires,null,status,List.of()); }
    @Test void tenantMismatchNeverDecryptsOrFallsBackToUnscopedLookup() {
        when(repository.findById(tenant,id)).thenReturn(Optional.empty());
        assertThrows(BizException.class,()->facade.getUsableCredential(tenant,id));
        verify(repository,never()).findByIdOnly(any());verifyNoInteractions(encryptor);
    }
    @Test void expiredOrCancelledCredentialsNeverReachPlatform() {
        when(repository.findById(tenant,id)).thenReturn(Optional.of(account(Instant.now().minusSeconds(1),"ACTIVE")));
        assertThrows(BizException.class,()->facade.getUsableCredential(tenant,id));
        when(repository.findById(tenant,id)).thenReturn(Optional.of(account(Instant.now().plusSeconds(3600),"UNBOUND")));
        assertThrows(BizException.class,()->facade.getUsableCredential(tenant,id));verifyNoInteractions(encryptor);
    }
    @Test void readsFreshPersistedTokenWithoutOwningRefreshOrApplicationSecret() {
        when(repository.findById(tenant,id)).thenReturn(Optional.of(account(Instant.now().plusSeconds(3600),"ACTIVE")));
        when(encryptor.decrypt("encrypted")).thenReturn("persisted-token");
        var credential=facade.getUsableCredential(tenant,id);assertEquals("persisted-token",credential.accessToken());assertEquals("123",credential.advertiserId().value());
        verify(repository,never()).save(any());
    }
}
