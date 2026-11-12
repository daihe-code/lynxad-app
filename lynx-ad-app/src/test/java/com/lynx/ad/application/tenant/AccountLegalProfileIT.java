package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.port.OtpStore;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.TenantAuthSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes=LynxAdApplication.class)
@AutoConfigureMockMvc @ActiveProfiles("test")
class AccountLegalProfileIT extends AbstractContainerIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OtpStore otpStore;
    @Autowired PasswordEncoder passwords;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    static final String ROOT="/api/v1/tenant/account";
    static final String CREDIT="91310000123456789A";
    JsonNode call(String method,String url,String body,String token) throws Exception {
        var request=switch(method){case "PUT"->put(url);case "POST"->post(url);default->get(url);};
        request.contentType(MediaType.APPLICATION_JSON);
        if(body!=null)request.content(body);
        if(token!=null)request.header("Authorization","Bearer "+token);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String reviewer() throws Exception {
        String phone = "13810900372";
        String code = "485231";
        otpStore.saveAdminLogin(new PhoneNumber(phone), passwords.encode(code), Duration.ofMinutes(5));
        return call("POST", "/api/v1/admin/auth/login", json.writeValueAsString(java.util.Map.of("phone", phone, "code", code)), null)
                .path("data").path("token").asText();
    }
    String company(String name,String credit){return "{\"companyName\":\""+name+"\",\"creditCode\":\""+credit+"\",\"nickname\":\"测试联系人\",\"industry\":\"电子商务\",\"email\":\"qa@example.invalid\"}";}
    String invoice(String title,String tax){return "{\"title\":\""+title+"\",\"type\":\"增值税专用发票\",\"taxNo\":\""+tax+"\",\"bank\":\"测试银行 1234567890\"}";}
    @Test void manualReviewAndInvoicePersistWithoutLeakingAcrossTenants() throws Exception {
        var a=TenantAuthSupport.onboard(mvc,json,"13900008901","隔离企业甲");
        var b=TenantAuthSupport.onboard(mvc,json,"13900008902","隔离企业乙");
        assertEquals("UNSUBMITTED",call("GET",ROOT+"/legal-profile",null,a.token()).path("data").path("verificationStatus").asText());
        var submitted=call("PUT",ROOT+"/company",company("隔离企业甲",CREDIT),a.token());
        assertEquals(0,submitted.path("code").asInt());
        assertEquals("PENDING",submitted.path("data").path("verificationStatus").asText());
        int revision=submitted.path("data").path("revision").asInt();
        assertNotEquals(0,call("PUT",ROOT+"/invoice",invoice("隔离企业甲",CREDIT),a.token()).path("code").asInt(),"待核验不可保存发票");
        assertEquals("UNSUBMITTED",call("GET",ROOT+"/legal-profile",null,b.token()).path("data").path("verificationStatus").asText());
        String review="{\"revision\":"+revision+",\"status\":\"APPROVED\",\"reason\":\"材料核对一致\"}";
        assertEquals(403,call("POST","/api/v1/admin/qualifications/"+a.tenantId()+"/review",review,a.token()).path("code").asInt());
        String admin=reviewer();
        assertEquals(0,call("POST","/api/v1/admin/qualifications/"+a.tenantId()+"/review",review,admin).path("code").asInt());
        assertNotEquals(0,call("POST","/api/v1/admin/qualifications/"+a.tenantId()+"/review",review,admin).path("code").asInt(),"旧版本审核不可重复覆盖");
        assertEquals("APPROVED",call("GET",ROOT+"/legal-profile",null,a.token()).path("data").path("verificationStatus").asText());
        assertNotEquals(0,call("PUT",ROOT+"/invoice",invoice("企业乙",CREDIT),a.token()).path("code").asInt());
        assertNotEquals(0,call("PUT",ROOT+"/invoice",invoice("隔离企业甲","91310000123456789B"),a.token()).path("code").asInt());
        assertEquals(0,call("PUT",ROOT+"/invoice",invoice("隔离企业甲",CREDIT),a.token()).path("code").asInt());
        var persisted=call("GET",ROOT+"/legal-profile",null,a.token()).path("data");
        assertEquals(CREDIT,persisted.path("invoice").path("taxNo").asText());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE tenant_id=? AND object_type='INVOICE_PROFILE'",Integer.class,a.tenantId()));
        assertTrue(call("GET",ROOT+"/legal-profile",null,b.token()).path("data").path("invoice").isNull());
        assertEquals("APPROVED",call("PUT",ROOT+"/company",company("隔离企业甲",CREDIT).replace("测试联系人","更新联系人"),a.token()).path("data").path("verificationStatus").asText());
        assertEquals("PENDING",call("PUT",ROOT+"/company",company("变更企业甲",CREDIT),a.token()).path("data").path("verificationStatus").asText());
        assertTrue(call("GET",ROOT+"/legal-profile",null,a.token()).path("data").path("invoice").isNull(),"变更企业后旧发票资料失效");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM platform_admin_audit WHERE tenant_id=? AND action_type='QUALIFICATION_REVIEW'",Integer.class,a.tenantId())>0);
    }
    @Test void validationPendingLockRejectionAndResubmission() throws Exception {
        var a=TenantAuthSupport.onboard(mvc,json,"13900008903","核验企业");
        assertNotEquals(0,call("PUT",ROOT+"/company",company("核验企业","123"),a.token()).path("code").asInt());
        var pending=call("PUT",ROOT+"/company",company("核验企业",CREDIT),a.token()).path("data");
        assertNotEquals(0,call("PUT",ROOT+"/company",company("其他企业",CREDIT),a.token()).path("code").asInt());
        assertEquals(0,call("PUT",ROOT+"/company",company("核验企业",CREDIT).replace("测试联系人","新联系人"),a.token()).path("code").asInt());
        String admin=reviewer(),url="/api/v1/admin/qualifications/"+a.tenantId()+"/review";
        assertNotEquals(0,call("POST",url,"{\"revision\":"+pending.path("revision").asInt()+",\"status\":\"REJECTED\",\"reason\":\"\"}",admin).path("code").asInt());
        assertEquals(0,call("POST",url,"{\"revision\":"+pending.path("revision").asInt()+",\"status\":\"REJECTED\",\"reason\":\"需补充资料\"}",admin).path("code").asInt());
        assertEquals("需补充资料",call("GET",ROOT+"/legal-profile",null,a.token()).path("data").path("reviewReason").asText());
        assertEquals("PENDING",call("PUT",ROOT+"/company",company("核验企业",CREDIT),a.token()).path("data").path("verificationStatus").asText());
    }
    @Test void plansUsePrdQuotas() throws Exception {
        var a=TenantAuthSupport.onboard(mvc,json,"13900008904","配额企业");
        var items=call("GET","/api/v1/billing/plans",null,a.token()).path("data").path("items");
        assertEquals(1,items.get(0).path("adAccountLimit").asInt());
        assertEquals(3,items.get(1).path("adAccountLimit").asInt());
        assertEquals(10,items.get(2).path("adAccountLimit").asInt());
    }
    @Test void membersCanReadButCannotSubmitOrSaveInvoice() throws Exception {
        var a=TenantAuthSupport.onboard(mvc,json,"13900008905","成员权限企业");
        jdbc.update("UPDATE tenant_member SET member_role='MEMBER' WHERE tenant_id=?",a.tenantId());
        String member=TenantAuthSupport.smsLogin(mvc,json,"13900008905");
        assertEquals(0,call("GET",ROOT+"/legal-profile",null,member).path("code").asInt());
        assertEquals(403,call("PUT",ROOT+"/company",company("成员权限企业",CREDIT),member).path("code").asInt());
        assertEquals(403,call("PUT",ROOT+"/invoice",invoice("成员权限企业",CREDIT),member).path("code").asInt());
    }
    @Test void memberReadsTenantContactsAndFullReviewHistorySurvivesResubmission() throws Exception {
        var owner=TenantAuthSupport.onboard(mvc,json,"13900008906","共享企业资料");
        var pending=call("PUT",ROOT+"/company",company("共享企业资料",CREDIT),owner.token()).path("data");
        assertEquals(0,call("POST","/api/v1/tenant/members/invite","{\"phone\":\"13900008907\"}",owner.token()).path("code").asInt());
        String member=TenantAuthSupport.smsLogin(mvc,json,"13900008907");
        var shared=call("GET",ROOT+"/legal-profile",null,member).path("data");
        assertEquals("测试联系人",shared.path("nickname").asText());
        assertEquals("电子商务",shared.path("industry").asText());
        assertEquals("qa@example.invalid",shared.path("email").asText());
        assertEquals("13900008906",shared.path("contactPhone").asText());
        String reason="资".repeat(512),admin=reviewer();
        assertEquals(0,call("POST","/api/v1/admin/qualifications/"+owner.tenantId()+"/review",
                json.writeValueAsString(java.util.Map.of("revision",pending.path("revision").asInt(),"status","REJECTED","reason",reason)),admin).path("code").asInt());
        assertEquals("PENDING",call("PUT",ROOT+"/company",company("共享企业资料",CREDIT),owner.token()).path("data").path("verificationStatus").asText());
        assertEquals(reason,jdbc.queryForObject("SELECT reason FROM tenant_qualification_review WHERE tenant_id=?",String.class,owner.tenantId()));
    }
}
