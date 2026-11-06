package com.lynx.ad.modules.identity.application;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Signed immutable task scope. The engine rechecks it before starting another paid operation. */
@Service
public class EngineExecutionTickets {
    private final AccountBindingRegistry bindings;private final JdbcTemplate jdbc;private final ObjectMapper json;private final byte[] secret;
    private final boolean enabled;
    private QianchuanCredentialFacade credentials;
    @org.springframework.beans.factory.annotation.Autowired public void credentials(QianchuanCredentialFacade value) { credentials=value; }
    public EngineExecutionTickets(AccountBindingRegistry bindings,JdbcTemplate jdbc,ObjectMapper json,LynxAdProperties properties,
            @Value("${lynx.ad.workspace.execution-tickets-enabled:false}") boolean enabled) {
        this.bindings=bindings;this.jdbc=jdbc;this.json=json;this.enabled=enabled;
        secret=properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
    }
    public record Ticket(AccountBindingRegistry.ExecutionScope scope,String taskId,long expiresAt) {}
    public String header(JsonNode body) {
        if(!enabled)return null;
        String tenant=body.path("store_id").asText();if(tenant.isBlank())throw new BizException(403,"任务必须指定工作空间");
        if(TenantContext.tenantId().isPresent()&&!tenant.equals(TenantContext.requireTenantId().value()))throw new BizException(403,"任务工作空间不匹配");
        String account=body.path("account_id").asText(body.path("account").path("account_id").asText());
        String task=body.path("ai_plan_task_id").asText();
        AccountBindingRegistry.ExecutionScope remembered=null;
        if(!task.isBlank()) {
            var rows=jdbc.queryForList("SELECT scope_json FROM workspace_engine_scope WHERE tenant_id=? AND task_id=?",String.class,tenant,task);
            if(!rows.isEmpty())try {
                remembered=json.readValue(rows.getFirst(),AccountBindingRegistry.ExecutionScope.class);
                if(!account.isBlank()&&!account.equals(remembered.bindingId()))throw new BizException(403,"任务账户不匹配");
                account=remembered.bindingId();bindings.validate(remembered);
                if(TenantContext.tenantId().isPresent()&&!remembered.actorUserId().equals(TenantContext.userId().value()))throw new BizException(403,"任务责任人不匹配");
            }catch(java.io.IOException ex){throw new IllegalStateException("Invalid saved execution scope",ex);}
        }
        String originalActor=null;
        if(account.isBlank()&&!task.isBlank()) {
            Set<String> accounts=new HashSet<>();
            for(String state:jdbc.queryForList("SELECT state_json FROM delivery_creation WHERE tenant_id=?",String.class,tenant))try {
                var s=json.readTree(state);
                boolean matches=task.equals(s.path("enginePlanId").asText());
                for(var material:s.path("materials"))matches|=task.equals(material.path("enginePlanId").asText());
                if(matches&&s.hasNonNull("accountId")){accounts.add(s.path("accountId").asText());originalActor=s.path("userId").asText(null);}
            }catch(java.io.IOException ex){throw new IllegalStateException("Invalid creation scope",ex);}
            if(accounts.size()==1)account=accounts.iterator().next();
        }
        if(account.isBlank())return null; // Tenant-only asset review has no advertising-account execution scope.
        String actor=TenantContext.tenantId().isPresent()?TenantContext.userId().value():body.path("operator").path("user_id").asText(originalActor);
        var scope=remembered!=null?remembered:bindings.executionScope(tenant,account,actor);
        bindings.validatePaid(scope);
        if(credentials!=null)credentials.getUsableCredential(new com.lynx.ad.common.domain.Ids.TenantId(tenant),new com.lynx.ad.common.domain.Ids.AccountId(account));
        try {
            var ticket=new Ticket(scope,task,Instant.now().plusSeconds(86400).getEpochSecond());
            String data=Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(ticket));
            String signed=data+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(data));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(Map.of("tenant_id",tenant,"binding_id",account,"task_id",task,"ticket",signed)));
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    public void remember(String header,JsonNode response) {
        if(header==null||!enabled)return;
        String task=response.path("ai_plan_task_id").asText(response.path("id").asText());
        if(task.isBlank())return;
        try {
            var envelope=json.readTree(Base64.getUrlDecoder().decode(header));
            String signed=envelope.path("ticket").asText();
            var ticket=json.readValue(Base64.getUrlDecoder().decode(signed.split("\\.")[0]),Ticket.class);
            var s=ticket.scope();bindings.validate(s);
            jdbc.update("INSERT INTO workspace_engine_scope(tenant_id,task_id,binding_id,scope_json) SELECT ?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM workspace_engine_scope WHERE tenant_id=? AND task_id=?)",s.tenantId(),task,s.bindingId(),json.writeValueAsString(s),s.tenantId(),task);
        }catch(java.io.IOException ex){throw new IllegalStateException("Invalid execution scope receipt",ex);}
    }
    private byte[] mac(String data) {
        try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));}
        catch(java.security.GeneralSecurityException ex){throw new IllegalStateException(ex);}
    }
    public void validate(String ticket,String tenant,String binding,String task) {
        try {
            if(ticket==null||ticket.length()>8192)throw new IllegalArgumentException();
            var parts=ticket.split("\\.");if(parts.length!=2||!MessageDigest.isEqual(mac(parts[0]),Base64.getUrlDecoder().decode(parts[1])))throw new IllegalArgumentException();
            var scope=json.readValue(Base64.getUrlDecoder().decode(parts[0]),Ticket.class);
            if(scope.expiresAt()<Instant.now().getEpochSecond()||!Objects.equals(tenant,scope.scope().tenantId())||!Objects.equals(binding,scope.scope().bindingId())||!Objects.equals(task,scope.taskId()))throw new IllegalArgumentException();
            bindings.validatePaid(scope.scope());
            if(credentials!=null)credentials.getUsableCredential(new com.lynx.ad.common.domain.Ids.TenantId(tenant),new com.lynx.ad.common.domain.Ids.AccountId(binding));
        }catch(BizException ex){throw ex;}catch(Exception ex){throw new BizException(403,"任务执行授权无效或已过期");}
    }
}
