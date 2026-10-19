package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.common.domain.CompanyScale;
import com.lynx.ad.modules.admin.application.AdminAuthService;
import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.audit.application.SuccessAuditCommand;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import static com.lynx.ad.modules.admin.infrastructure.AdminAccountStore.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-owned legal and invoice data. Validation never implies qualification approval. */
@Service
public class AccountLegalService {
    private final JdbcTemplate jdbc;
    private final TenantQuerySupport users;
    private final AdminAuthService adminAuth;
    private final AdminAccountStore admins;
    private final AuditRecorder audit;
    public AccountLegalService(JdbcTemplate jdbc, TenantQuerySupport users,
            AdminAuthService adminAuth, AdminAccountStore admins, AuditRecorder audit) {
        this.jdbc=jdbc;this.users=users;this.adminAuth=adminAuth;this.admins=admins;this.audit=audit;
    }
    public record Company(String companyName,String creditCode,String nickname,String industry,String email,String companyScale) {}
    public record Invoice(String title,String type,String taxNo,String bank) {}
    public record Review(int revision,String status,String reason) {}
    public record Legal(String tenantId,String companyName,String creditCode,String verificationStatus,String reviewReason,
            int revision,Instant submittedAt,Instant reviewedAt,
            String nickname,String industry,String email,String companyScale,String contactPhone,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS) Invoice invoice) {}
    private static final RowMapper<Legal> ROW=(r,n)->new Legal(r.getString("tenant_id"),r.getString("company_name"),r.getString("credit_code"),
            r.getString("verification_status"),r.getString("review_reason"),r.getInt("revision"),instant(r,"submitted_at"),instant(r,"reviewed_at"),r.getString("contact_name"),r.getString("industry"),r.getString("email"),r.getString("company_scale"),r.getString("contact_phone"),null);
    private static final String SELECT_LEGAL="SELECT p.*,u.phone AS contact_phone FROM tenant_legal_profile p LEFT JOIN sys_user u ON u.id=p.contact_user_id";
    private Optional<Legal> find(String id) { return jdbc.query(SELECT_LEGAL+" WHERE p.tenant_id=?",ROW,id).stream().findFirst(); }
    private Invoice invoice(String id) {
        return jdbc.query("SELECT * FROM tenant_invoice_profile WHERE tenant_id=?",(r,n)->new Invoice(r.getString("title"),r.getString("invoice_type"),r.getString("tax_no"),r.getString("bank")),id).stream().findFirst().orElse(null);
    }
    private Legal withInvoice(Legal p) { return new Legal(p.tenantId(),p.companyName(),p.creditCode(),p.verificationStatus(),p.reviewReason(),p.revision(),p.submittedAt(),p.reviewedAt(),p.nickname(),p.industry(),p.email(),p.companyScale(),p.contactPhone(),invoice(p.tenantId())); }
    public Legal get() {
        String id=TenantContext.requireTenantId().value();
        return find(id).map(this::withInvoice).orElseGet(()->{
            var u=users.current(TenantContext.userId());
            boolean owner=TenantContext.require().memberRole().orElse(null)==MemberRole.OWNER;
            return new Legal(id,u.tenantName(),"","UNSUBMITTED",null,0,null,null,owner?u.nickname():null,owner?u.industry():null,owner?u.email():null,owner?u.companyScale():null,owner?u.phone():null,null);
        });
    }
    private String owner() {
        var p=TenantContext.require();
        if(p.memberRole().orElse(null)!=MemberRole.OWNER)throw new BizException(403,"仅账户所有者可修改企业和发票资料");
        return TenantContext.requireTenantId().value();
    }
    // A stable parent row serializes first submission, invoice saves and reviews on both SQL dialects.
    private void lock(String id) {
        if(jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE",String.class,id).isEmpty())throw new BizException(404,"企业账户不存在");
    }
    private static String field(String value,String name,int max) {
        String s=value==null?"":value.trim();
        if(s.isEmpty()||s.length()>max)throw new BizException(1904,name+"必填且不能超过"+max+"个字符");
        return s;
    }
    @Transactional
    public Legal company(Company request) {
        String id=owner();lock(id);
        String name=field(request.companyName(),"企业名称",200);
        String credit=field(request.creditCode(),"统一社会信用代码",18).toUpperCase(Locale.ROOT);
        if(!credit.matches("[A-Z0-9]{18}"))throw new BizException(1904,"统一社会信用代码须为18位字母或数字");
        String nickname=field(request.nickname(),"联系人",64),industry=field(request.industry(),"所属行业",100);
        String email=request.email()==null?"":request.email().trim();
        if(email.length()>200||!email.isEmpty()&&!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new BizException(1904,"邮箱格式不正确");
        String scale=CompanyScale.parse(request.companyScale()).valueOrNull();
        var old=find(id).orElse(null);
        boolean changed=old==null||!name.equals(old.companyName())||!credit.equals(old.creditCode());
        if(old!=null&&"PENDING".equals(old.verificationStatus())&&changed)throw new BizException(1904,"人工审核中，暂不能修改企业名称和信用代码");
        boolean submit=changed||old!=null&&"REJECTED".equals(old.verificationStatus());
        if(submit) {
            var now=ts(Instant.now());
            if(old==null)jdbc.update("INSERT INTO tenant_legal_profile(tenant_id,company_name,credit_code,verification_status,revision,submitted_at) VALUES(?,?,?,'PENDING',1,?)",id,name,credit,now);
            else jdbc.update("UPDATE tenant_legal_profile SET company_name=?,credit_code=?,verification_status='PENDING',review_reason=NULL,revision=revision+1,submitted_at=?,reviewed_at=NULL,reviewed_by=NULL WHERE tenant_id=?",name,credit,now,id);
            // Previous invoice identity is no longer authoritative after a legal entity change.
            if(changed)jdbc.update("DELETE FROM tenant_invoice_profile WHERE tenant_id=?",id);
        }
        jdbc.update("UPDATE tenant_legal_profile SET contact_name=?,industry=?,email=?,company_scale=?,contact_user_id=? WHERE tenant_id=?",nickname,industry,email,scale,TenantContext.userId().value(),id);
        recordAccount(id,"企业资料","LEGAL_PROFILE");
        return get();
    }
    @Transactional
    public Legal saveInvoice(Invoice request) {
        String id=owner();lock(id);
        var p=find(id).orElseThrow(()->new BizException(1904,"请先提交企业资料并完成人工核验"));
        if(!"APPROVED".equals(p.verificationStatus()))throw new BizException(1904,"企业资质经人工审核通过后方可保存发票信息");
        String title=field(request.title(),"发票抬头",200),tax=field(request.taxNo(),"纳税人识别号",18).toUpperCase(Locale.ROOT);
        if(!title.equals(p.companyName())||!tax.equals(p.creditCode()))throw new BizException(1904,"发票抬头和税号须与已核验企业一致");
        if(!"增值税专用发票".equals(request.type()))throw new BizException(1904,"当前支持增值税专用发票");
        String bank=field(request.bank(),"开户行及账号",200);
        jdbc.update("DELETE FROM tenant_invoice_profile WHERE tenant_id=?",id);
        jdbc.update("INSERT INTO tenant_invoice_profile(tenant_id,title,invoice_type,tax_no,bank,updated_at) VALUES(?,?,?,?,?,?)",id,title,request.type(),tax,bank,ts(Instant.now()));
        recordAccount(id,"更新发票资料","INVOICE_PROFILE");
        return get();
    }
    private void recordAccount(String id,String label,String type) {
        var user=users.current(TenantContext.userId());
        audit.record(new SuccessAuditCommand(TenantContext.requireTenantId(),TenantContext.userId(),user.nickname()==null?user.phone():user.nickname(),
                AuditActionType.ACCOUNT,type,id,label,RequestMeta.ip().orElse(null),TenantContext.require().sessionId().orElse(null),RequestMeta.userAgent().orElse(null),null));
    }
    public record Queue(List<Legal> records,long total,int pageNum,int pageSize) {}
    public Queue queue(String status,int pageNum) {
        adminAuth.require();
        if(!Set.of("PENDING","APPROVED","REJECTED").contains(status))throw new BizException(1904,"审核状态不正确");
        int page=Math.min(100000,Math.max(1,pageNum));
        var rows=jdbc.query(SELECT_LEGAL+" WHERE p.verification_status=? ORDER BY p.submitted_at,p.tenant_id LIMIT 20 OFFSET ?",ROW,status,(page-1)*20);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM tenant_legal_profile WHERE verification_status=?",Long.class,status);
        return new Queue(rows,total,page,20);
    }
    @Transactional
    public Legal review(String tenant,Review request) {
        var a=adminAuth.require();lock(tenant);
        if(!Set.of("APPROVED","REJECTED").contains(request.status()==null?"":request.status()))throw new BizException(1904,"请选择通过或驳回");
        String reason=field(request.reason(),"审核依据或驳回原因",512);
        int updated=jdbc.update("UPDATE tenant_legal_profile SET verification_status=?,review_reason=?,reviewed_at=?,reviewed_by=?,revision=revision+1 WHERE tenant_id=? AND verification_status='PENDING' AND revision=?",
                request.status(),reason,ts(Instant.now()),a.id(),tenant,request.revision());
        if(updated!=1)throw new BizException(1904,"资料状态或版本已变化，请刷新后重新审核");
        String reviewId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tenant_qualification_review(id,tenant_id,submitted_revision,decision,reason,admin_id,reviewed_at) VALUES(?,?,?,?,?,?,?)",reviewId,tenant,request.revision(),request.status(),reason,a.id(),ts(Instant.now()));
        admins.audit(a,"QUALIFICATION_REVIEW","SUCCESS",null,tenant,"企业资质人工审核："+request.status()+"；审核记录："+reviewId);
        return withInvoice(find(tenant).orElseThrow());
    }
}
