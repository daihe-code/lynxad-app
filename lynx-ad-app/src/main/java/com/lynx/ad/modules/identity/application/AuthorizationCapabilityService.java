package com.lynx.ad.modules.identity.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Observed account/upload evidence is not a complete OAuth scope declaration. */
@Service
public class AuthorizationCapabilityService {
    private final JdbcTemplate jdbc;private final OauthAccountRepository accounts;
    public AuthorizationCapabilityService(JdbcTemplate jdbc,OauthAccountRepository accounts){this.jdbc=jdbc;this.accounts=accounts;}
    public record Check(String accountId,String status,String accountStatus,String materialStatus,String source,Instant checkedAt,String message) {}
    public Check inspect(TenantId tenant,String id) {
        var account=accounts.findById(tenant,new AccountId(id)).orElseThrow(()->new BizException(1201,"授权账户不存在"));
        String accountStatus="ACTIVE".equals(account.status())?account.awemes().isEmpty()?"NEEDS_SELECTION":"AVAILABLE":"UNAVAILABLE";
        String materialStatus="UNKNOWN",source="PLATFORM_SCOPES_NOT_RETURNED";
        if(account.authorizedAt()!=null) {
            var failures=jdbc.queryForList("SELECT error_message FROM creative_asset_platform WHERE tenant_id=? AND account_id=? AND updated_at>=? AND status='FAILED' ORDER BY updated_at DESC LIMIT 20",String.class,tenant.value(),id,Timestamp.from(account.authorizedAt()));
            if(failures.stream().anyMatch(AuthorizationCapabilityService::sensitivePermissionDenied)){materialStatus="MISSING";source="PLATFORM_UPLOAD_REJECTION";}
        }
        String status="MISSING".equals(materialStatus)?"LIMITED":"UNAVAILABLE".equals(accountStatus)?"UNAVAILABLE":"UNKNOWN";
        String message="MISSING".equals(materialStatus)?"素材管理授权未完成，相关素材生成及投放能力受限，请补充授权。":"NEEDS_SELECTION".equals(accountStatus)?"尚未获取可投放抖音号，请检查是否选择了所需广告账户及投放对象。":"UNAVAILABLE".equals(accountStatus)?"账户授权不可用，请重新授权。":"账户连接状态已核对；平台未返回可核验的完整权限清单，素材管理权限尚待核验。";
        return new Check(id,status,accountStatus,materialStatus,source,Instant.now(),message);
    }
    public Check record(TenantId tenant,String id) {
        var result=inspect(tenant,id);var account=accounts.findById(tenant,new AccountId(id)).orElseThrow();
        jdbc.update("INSERT INTO oauth_capability_check(id,tenant_id,account_id,grant_id,account_status,material_status,source,checked_at) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),tenant.value(),id,account.grantId(),result.accountStatus(),result.materialStatus(),result.source(),Timestamp.from(result.checkedAt()));
        return result;
    }
    public void requireMaterials(TenantId tenant,String id) {
        var check=inspect(tenant,id);
        if(!"AVAILABLE".equals(check.accountStatus()))throw new BizException(1205,"账户授权或投放对象不可用，请在账户管理中重新授权并选择投放对象");
        if("MISSING".equals(check.materialStatus()))throw new BizException(1205,"素材管理授权未完成，请在账户管理中补充敏感物料授权");
    }
    public static boolean sensitivePermissionDenied(String message) {
        return message!=null&&message.contains("敏感物料")&&(message.contains("未授权")||message.contains("未开通")||message.contains("无权限")||message.contains("未勾选"));
    }
}
