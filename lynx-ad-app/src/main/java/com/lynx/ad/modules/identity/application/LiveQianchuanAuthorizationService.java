package com.lynx.ad.modules.identity.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.audit.application.*;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.billing.application.EntitlementQueryService;
import com.lynx.ad.modules.identity.api.dto.AccountVo;
import com.lynx.ad.modules.identity.api.dto.OauthUrlResponse;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.identity.domain.service.OauthStateSigner;
import com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter;
import com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter.AuthorizedAdvertiser;
import com.lynx.ad.modules.identity.infrastructure.persistence.OauthAuthorizationStore;
import com.lynx.ad.modules.identity.infrastructure.persistence.OauthAuthorizationStore.*;
import com.lynx.ad.modules.tenant.application.TenantQuerySupport;
import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LiveQianchuanAuthorizationService {
    private final OauthAuthorizationStore store;
    private final LynxAdProperties properties;
    private final Encryptor encryptor;
    private final ObjectProvider<LiveOauthTokenAdapter> remote;
    private final OauthAccountRepository accounts;
    private final TenantQuerySupport tenants;
    private final EntitlementQueryService entitlements;
    private final AuditRecorder audit;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final OauthStateSigner signer;
    public record CallbackOutcome(boolean ok,String sessionId,String error) {}
    public record SessionView(String sessionId,String platform,String status,List<AuthorizedAdvertiser> accounts,
            List<String> boundAccountIds,int accountLimit,String returnPath,String message) {}
    private record CatalogResult(List<Aweme> awemes,String error) {}
    public LiveQianchuanAuthorizationService(OauthAuthorizationStore store,LynxAdProperties properties,Encryptor encryptor,
            ObjectProvider<LiveOauthTokenAdapter> remote,OauthAccountRepository accounts,TenantQuerySupport tenants,
            EntitlementQueryService entitlements,AuditRecorder audit,ObjectMapper json,PlatformTransactionManager tx) {
        this.store=store; this.properties=properties; this.encryptor=encryptor; this.remote=remote; this.accounts=accounts;
        this.tenants=tenants; this.entitlements=entitlements; this.audit=audit; this.json=json;
        transaction=new TransactionTemplate(tx); signer=new OauthStateSigner(properties.getCrypto().getAesKey());
    }
    public boolean enabled() { return properties.getOauth().getQianchuan().isLive(); }
    @PostConstruct public void configureApplication() { if(enabled()) currentApplication(); }
    private Application currentApplication() {
        if(!enabled() || !properties.isQianchuanLiveConfigured()) throw new BizException(1205,"千川授权应用尚未配置");
        var qc=properties.getOauth().getQianchuan();
        return store.configure("QIANCHUAN",qc.getAppId(),encryptor.encrypt(qc.getAppSecret()),qc.getRedirectUri());
    }
    private LiveOauthTokenAdapter api() {
        var adapter=remote.getIfAvailable(); if(adapter==null) throw new BizException(1205,"真实千川授权未启用"); return adapter;
    }
    public OauthUrlResponse start(TenantId tenant,UserId user,String returnPath) {
        requireMember(tenant,user);
        Application app=currentApplication(); String state=signer.sign(tenant,user);
        String destination="/accounts?tab=oauth".equals(returnPath)?returnPath:"/campaigns";
        store.createSession(tenant.value(),user.value(),app.id(),OauthAuthCodeAudit.sha256Hex(state),destination);
        String url=properties.getOauth().getQianchuan().getAuthorizeUrl()+"?app_id="+enc(app.appId())
            +"&redirect_uri="+enc(app.redirectUri())+"&state="+enc(state)+"&material_auth=1";
        return new OauthUrlResponse(url);
    }
    private void requireMember(TenantId tenant,UserId user) {
        var company=tenants.findById(tenant).orElseThrow(()->new BizException(403,"灵投账户不存在"));
        if(company.memberOf(user).isEmpty()) throw new BizException(403,"当前用户不是该灵投账户成员");
    }
    public CallbackOutcome callback(String code,String state) {
        Session session=null;
        try {
            var verified=signer.verify(state);
            session=store.byState(OauthAuthCodeAudit.sha256Hex(state));
            if(!session.tenantId().equals(verified.tenantId().value()) || !session.userId().equals(verified.userId().value()))
                throw new BizException(1206,"授权状态无效");
            requireMember(verified.tenantId(),verified.userId());
            if(code==null || code.isBlank()) throw new BizException(1206,"未收到授权码，请重新授权");
            if(!session.expiresAt().isAfter(Instant.now())) throw new BizException(1206,"授权已超时，请重新发起");
            if(!store.claim(session,OauthAuthCodeAudit.sha256Hex(code))) {
                if(List.of("SELECTING","COMPLETED","DISCOVERY_FAILED").contains(session.status())) return new CallbackOutcome(true,session.id(),null);
                return new CallbackOutcome(false,null,"授权回调已处理或正在处理中，请勿重复提交");
            }
            Application app=store.application(session.applicationId());
            if(!"QIANCHUAN".equals(app.platform())) throw new BizException(1206,"授权应用类型不匹配");
            var token=api().exchangeToken(app.appId(),encryptor.decrypt(app.secretEnc()),code);
            Session claimed=session;
            transaction.executeWithoutResult(tx->store.createGrant(claimed,encryptor.encrypt(token.accessToken()),encryptor.encrypt(token.refreshToken()),token.accessExpiresAt(),token.refreshExpiresAt()));
            session=store.session(session.id(),session.tenantId(),session.userId(),false);
            discover(session);
            return new CallbackOutcome(true,session.id(),null);
        } catch(Exception ex) {
            String message=safeMessage(ex);
            try { if(session!=null) {
                Session latest=store.session(session.id(),session.tenantId(),session.userId(),false);
                if(latest.grantId()!=null) {
                    store.failed(latest.id(),"DISCOVERY_FAILED",message);
                    return new CallbackOutcome(true,latest.id(),null);
                }
                if("EXCHANGING".equals(latest.status())) store.failed(latest.id(),"FAILED",message);
            } } catch(Exception persistenceFailure) { message="授权处理暂时失败，请重新发起授权"; }
            return new CallbackOutcome(false,null,message);
        }
    }
    private void discover(Session session) {
        Grant grant=usableGrant(session.grantId(),session.tenantId());
        List<AuthorizedAdvertiser> catalog=api().discover(encryptor.decrypt(grant.accessEnc()));
        if(catalog.isEmpty()) throw new BizException(1205,"未找到已授权的千川广告账户，请确认应用权限和账户授权范围");
        store.catalog(session.id(),serialize(catalog));
        if(catalog.size()==1) {
            try { bind(session.id(),new TenantId(session.tenantId()),new UserId(session.userId()),List.of(catalog.getFirst().advertiserId())); }
            catch(BizException ex) { store.failed(session.id(),"SELECTING",safeMessage(ex)); }
        }
    }
    public SessionView view(String id,TenantId tenant,UserId user) {
        Session s=owned(id,tenant,user);
        return new SessionView(s.id(),"QIANCHUAN",s.status(),catalog(s),selected(s),entitlements.adAccountLimit(tenant),s.returnPath(),s.error());
    }
    public SessionView retry(String id,TenantId tenant,UserId user) {
        Session s=owned(id,tenant,user);
        if(!"DISCOVERY_FAILED".equals(s.status())) return view(id,tenant,user);
        if(!store.retryDiscovery(id)) return view(id,tenant,user);
        try { discover(s); } catch(Exception ex) { store.failed(id,"DISCOVERY_FAILED",safeMessage(ex)); }
        return view(id,tenant,user);
    }
    private Session owned(String id,TenantId tenant,UserId user) {
        requireMember(tenant,user);
        Session s=store.session(id,tenant.value(),user.value(),false);
        if(!"COMPLETED".equals(s.status()) && !s.expiresAt().isAfter(Instant.now())) throw new BizException(1206,"授权会话已超时，请重新发起授权");
        return s;
    }
    public List<AccountVo> bind(String id,TenantId tenant,UserId user,List<String> selected) {
        Session s=owned(id,tenant,user);
        if("COMPLETED".equals(s.status())) return bound(tenant,selected(s));
        if(!"SELECTING".equals(s.status())) throw new BizException(1206,"授权账户尚未同步完成");
        List<String> ids=selected==null?List.of():selected.stream().distinct().toList();
        if(ids.isEmpty()) throw new BizException(1001,"请选择需要绑定的千川广告账户");
        Map<String,AuthorizedAdvertiser> candidates=new LinkedHashMap<>(); catalog(s).forEach(a->candidates.put(a.advertiserId(),a));
        if(!candidates.keySet().containsAll(ids)) throw new BizException(403,"所选账户不在本次授权范围内");
        Grant grant=usableGrant(s.grantId(),tenant.value()); String access=encryptor.decrypt(grant.accessEnc());
        Map<String,CatalogResult> awemes=new LinkedHashMap<>();
        for(String adv:ids) awemes.put(adv,readAwemes(access,adv));
        List<String> saved=transaction.execute(tx->{
            Session locked=store.session(id,tenant.value(),user.value(),true);
            if("COMPLETED".equals(locked.status())) return selected(locked);
            if(!"SELECTING".equals(locked.status())) throw new BizException(1206,"授权会话状态已变化");
            store.lockTenant(tenant.value());
            Grant latestGrant=store.grant(grant.id(),tenant.value());
            if(!"ACTIVE".equals(latestGrant.status())) throw new BizException(1211,"授权已失效，请重新授权");
            List<OauthAccount> existing=accounts.findByTenant(tenant);
            long active=existing.stream().filter(this::active).count();
            long additional=ids.stream().filter(adv->existing.stream().noneMatch(a->a.platform()==Platform.QIANCHUAN && a.advertiserId().value().equals(adv) && active(a))).count();
            int limit=entitlements.adAccountLimit(tenant);
            if(active+additional>limit) throw new BizException(1206,"当前套餐最多绑定 "+limit+" 个广告账户，请减少选择或取消其他绑定");
            List<String> bound=new ArrayList<>();
            for(String adv:ids) {
                OauthAccount old=existing.stream().filter(a->a.platform()==Platform.QIANCHUAN && a.advertiserId().value().equals(adv)).findFirst().orElse(null);
                AuthorizedAdvertiser candidate=candidates.get(adv); CatalogResult list=awemes.get(adv);
                OauthAccount account=new OauthAccount(old==null?new AccountId(UUID.randomUUID().toString()):old.id(),tenant,Platform.QIANCHUAN,new AdvertiserId(adv),candidate.accountName(),
                    new EncryptedToken(latestGrant.accessEnc()),new EncryptedToken(latestGrant.refreshEnc()),latestGrant.accessExpiresAt(),latestGrant.refreshExpiresAt(),"ACTIVE",list.awemes(),user.value())
                    .withAuthorizedAt(Instant.now()).withAuthorizationBinding(latestGrant.id(),latestGrant.applicationId(),list.error());
                accounts.save(account); bound.add(account.id().value());
                audit.record(new SuccessAuditCommand(tenant,user,"商家",AuditActionType.OAUTH,"OAUTH_ACCOUNT",account.id().value(),"授权绑定巨量千川账户「"+account.accountName()+"」",null,null,null,null));
            }
            store.complete(id,serialize(bound)); return bound;
        });
        return bound(tenant,saved);
    }
    private List<AccountVo> bound(TenantId tenant,List<String> ids) { return accounts.findByTenant(tenant).stream().filter(a->ids.contains(a.id().value())).map(OauthQueryService::toVo).toList(); }
    private boolean active(OauthAccount a) { return "ACTIVE".equals(a.status()) && a.advertiserId().value().matches("[0-9]+") && (a.refreshExpiresAt()==null || a.refreshExpiresAt().isAfter(Instant.now())); }
    private Grant usableGrant(String id,String tenant) {
        Grant g=store.grant(id,tenant);
        if(!"ACTIVE".equals(g.status()) || (g.refreshExpiresAt()!=null && !g.refreshExpiresAt().isAfter(Instant.now()))) throw new BizException(1211,"授权已失效，请重新授权");
        if(!g.accessExpiresAt().isAfter(Instant.now().plusSeconds(60))) { refreshGrant(g,false); g=store.grant(id,tenant); }
        return g;
    }
    public void refresh(TenantId tenant,AccountId id,boolean force,boolean syncAwemes) {
        OauthAccount account=accounts.findById(tenant,id).orElseThrow(()->new BizException(1201,"授权账户不存在"));
        if(!"ACTIVE".equals(account.status()) || account.grantId()==null) throw new BizException(1211,"请先完成真实千川授权");
        Grant g=store.grant(account.grantId(),tenant.value()); refreshGrant(g,force);
        if(syncAwemes) {
            Grant updated=usableGrant(g.id(),tenant.value()); CatalogResult list=readAwemes(encryptor.decrypt(updated.accessEnc()),account.advertiserId().value());
            transaction.executeWithoutResult(tx->{
                store.lockTenant(tenant.value());
                OauthAccount current=accounts.findById(tenant,id).orElseThrow(()->new BizException(1201,"授权账户不存在"));
                if(!"ACTIVE".equals(current.status()) || !g.id().equals(current.grantId())) return;
                Grant latestGrant=store.grant(g.id(),tenant.value());
                if(!"ACTIVE".equals(latestGrant.status())) return;
                OauthAccount refreshed=new OauthAccount(current.id(),tenant,current.platform(),current.advertiserId(),current.accountName(),new EncryptedToken(latestGrant.accessEnc()),new EncryptedToken(latestGrant.refreshEnc()),latestGrant.accessExpiresAt(),latestGrant.refreshExpiresAt(),current.status(),list.awemes(),current.authorizedByUserId().orElse(null))
                    .withAuthorizedAt(current.authorizedAt()).withAuthorizationBinding(g.id(),latestGrant.applicationId(),list.error());
                accounts.save(refreshed);
            });
        }
    }
    private void refreshGrant(Grant grant,boolean force) {
        if(!"ACTIVE".equals(grant.status()) || grant.refreshEnc()==null || (grant.refreshExpiresAt()!=null && !grant.refreshExpiresAt().isAfter(Instant.now()))) {
            transaction.executeWithoutResult(tx->store.invalidate(grant.id(),grant.tenantId())); throw new BizException(1211,"授权刷新凭证已失效，请重新授权");
        }
        if(!force && grant.accessExpiresAt().isAfter(Instant.now().plus(OauthAccount.REFRESH_LEAD))) return;
        String owner=UUID.randomUUID().toString();
        if(!store.lockRefresh(grant,owner)) throw new BizException(1205,"授权凭证正在刷新，请稍后重试");
        try {
            Application app=store.application(grant.applicationId());
            if(!"QIANCHUAN".equals(app.platform())) throw new BizException(1205,"授权应用类型不匹配");
            var token=api().refreshToken(app.appId(),encryptor.decrypt(app.secretEnc()),encryptor.decrypt(grant.refreshEnc()));
            boolean saved=Boolean.TRUE.equals(transaction.execute(tx->store.saveRefresh(grant,owner,encryptor.encrypt(token.accessToken()),token.refreshToken()==null?grant.refreshEnc():encryptor.encrypt(token.refreshToken()),token.accessExpiresAt(),token.refreshExpiresAt())));
            if(!saved) throw new BizException(1205,"授权状态已变化，请重试");
        } catch(LiveOauthTokenAdapter.RemoteFailure ex) {
            if(ex.permanent()) transaction.executeWithoutResult(tx->store.invalidate(grant.id(),grant.tenantId()));
            throw ex;
        } finally { store.releaseRefresh(grant.id(),owner); }
    }
    public void changeLocalStatus(TenantId tenant,AccountId id,boolean remove) {
        transaction.executeWithoutResult(tx->{
            store.lockTenant(tenant.value());
            OauthAccount account=accounts.findById(tenant,id).orElseThrow(()->new BizException(1201,"授权账户不存在"));
            if(remove) {
                if(!account.canRemove(Instant.now())) throw new BizException(1211,"请先取消有效授权再删除记录");
                store.accountStatus(tenant.value(),id.value(),"DELETED");
            } else {
                if("DELETED".equals(account.status())) throw new BizException(1201,"授权账户不存在");
                store.accountStatus(tenant.value(),id.value(),"UNBOUND");
            }
        });
    }
    private CatalogResult readAwemes(String token,String advertiser) {
        try { return new CatalogResult(api().loadAwemes(token,advertiser).stream().map(a->new Aweme(new AwemeId(a.awemeId()),a.name(),a.avatar(),a.type(),a.primary(),a.sceneHint())).toList(),null); }
        catch(BizException ex) { return new CatalogResult(List.of(),"抖音号同步失败，请检查投放对象权限并点击刷新重试"); }
    }
    private List<AuthorizedAdvertiser> catalog(Session s) { return parse(s.catalogJson(),new TypeReference<List<AuthorizedAdvertiser>>(){}); }
    private List<String> selected(Session s) { return parse(s.selectedJson(),new TypeReference<List<String>>(){}); }
    private <T> List<T> parse(String value,TypeReference<List<T>> type) { try { return value==null?List.of():json.readValue(value,type); } catch(Exception ex) { throw new BizException(1205,"授权会话数据读取失败"); } }
    private String serialize(Object value) { try { return json.writeValueAsString(value); } catch(Exception ex) { throw new BizException(1205,"授权数据保存失败"); } }
    private static String safeMessage(Exception ex) { return ex instanceof BizException?ex.getMessage():"授权处理暂时失败，请重试；若换票未完成请重新授权"; }
    private static String enc(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
