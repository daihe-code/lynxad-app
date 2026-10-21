package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.tenant.application.*;
import com.lynx.ad.modules.tenant.api.dto.LoginResponse;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InboxLoginRecoveryTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);final JwtService jwt=mock(JwtService.class);
    final InboxDeliveryJob delivery=mock(InboxDeliveryJob.class);
    UserLoginJournal journal(){var value=new UserLoginJournal(jdbc,jwt,mock(UserRepository.class));value.deliveryJob(delivery);return value;}
    @Test void successfulLoginRecoversOnlyTheTenantFromTheVerifiedLoginToken(){
        var tenant=new TenantId("tenant");when(jwt.parse("synthetic-session")).thenReturn(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        journal().record(null,"PASSWORD",new LoginResponse("synthetic-session",3600,false,false));verify(delivery).recoverAfterLogin(tenant);
    }
    @Test void failedOrTenantlessLoginCannotTriggerRecovery(){
        journal().record(null,"PASSWORD",null);
        when(jwt.parse("no-tenant")).thenReturn(new TenantPrincipal(new UserId("user"),Optional.empty(),Optional.empty(),SysRole.USER));
        journal().record(null,"SMS",new LoginResponse("no-tenant",3600,true,false));verifyNoInteractions(delivery);
    }
    @Test void notificationRecoveryFailureDoesNotFailASuccessfulLogin(){
        var tenant=new TenantId("tenant");when(jwt.parse("synthetic-session")).thenReturn(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        doThrow(new IllegalStateException("outbox unavailable")).when(delivery).recoverAfterLogin(tenant);
        assertDoesNotThrow(()->journal().record(null,"SMS",new LoginResponse("synthetic-session",3600,false,false)));
    }
}
