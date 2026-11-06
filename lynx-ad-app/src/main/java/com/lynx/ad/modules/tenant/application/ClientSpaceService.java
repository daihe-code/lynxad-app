package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.identity.application.AccountBindingRegistry;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientSpaceService {
    private final JdbcTemplate jdbc;private final WorkspaceAccess access;private final WorkspaceFoundation foundation;private final AccountBindingRegistry bindings;
    private com.lynx.ad.modules.audit.application.AuditRecorder audit;
    @org.springframework.beans.factory.annotation.Autowired public void audit(com.lynx.ad.modules.audit.application.AuditRecorder value) {audit=value;}
    public ClientSpaceService(JdbcTemplate jdbc,WorkspaceAccess access,WorkspaceFoundation foundation,AccountBindingRegistry bindings) {
        this.jdbc=jdbc;this.access=access;this.foundation=foundation;this.bindings=bindings;
    }
    @Transactional public String save(String id,String name) {
        var member=access.require(MemberRole.OWNER,MemberRole.ADMIN);String tenant=member.tenantId();foundation.ensure(tenant);
        if(name==null||name.isBlank()||name.strip().length()>128)throw new BizException(1001,"分组名称应为1～128个字符");
        String clean=name.strip();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM client_space WHERE tenant_id=? AND name=? AND id<>?",Integer.class,tenant,clean,id==null?"":id)>0)throw new BizException(1001,"分组名称已存在");
        boolean creating=id==null;
        if(creating){id=UUID.randomUUID().toString();jdbc.update("INSERT INTO client_space(id,tenant_id,name) VALUES(?,?,?)",id,tenant,clean);}
        else if(jdbc.update("UPDATE client_space SET name=? WHERE id=? AND tenant_id=?",clean,id,tenant)!=1)throw new BizException(1001,"分组不存在");
        record(id,(creating?"新增客户分组：":"重命名客户分组：")+clean);
        return id;
    }
    @Transactional public void delete(String id) {
        var member=access.require(MemberRole.OWNER,MemberRole.ADMIN);String tenant=member.tenantId();foundation.ensure(tenant);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE tenant_id=? AND client_space_id=?",Integer.class,tenant,id)>0)throw new BizException(1001,"请先移出该分组下的账户");
        if(jdbc.update("DELETE FROM client_space WHERE id=? AND tenant_id=? AND is_default=FALSE",id,tenant)!=1)throw new BizException(1001,"默认分组不能删除或分组不存在");
        record(id,"删除客户分组");
    }
    @Transactional public void assign(String account,String client) {
        var member=access.require(MemberRole.OWNER,MemberRole.MEMBER,MemberRole.OPERATOR);foundation.ensure(member.tenantId());
        bindings.executionScope(member.tenantId(),account,member.userId());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM client_space WHERE id=? AND tenant_id=?",Integer.class,client,member.tenantId())!=1)throw new BizException(1001,"分组不属于当前工作空间");
        jdbc.update("UPDATE ad_account_binding SET client_space_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",client,account,member.tenantId());
        record(account,"调整账户客户分组："+client);
    }
    private void record(String id,String label) {
        if(audit==null)return;
        var p=com.lynx.ad.common.security.TenantContext.require();
        audit.record(new com.lynx.ad.modules.audit.application.SuccessAuditCommand(p.tenantId().orElseThrow(),p.userId(),"成员",
            com.lynx.ad.modules.audit.domain.model.AuditActionType.ACCOUNT,"CLIENT_SPACE",id,label,
            com.lynx.ad.common.security.RequestMeta.ip().orElse(null),p.sessionId().orElse(null),com.lynx.ad.common.security.RequestMeta.userAgent().orElse(null),null));
    }
}
