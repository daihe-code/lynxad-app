package com.lynx.ad.modules.tenant.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("tenant_member")
public class TenantMemberPo {
    private String tenantId;
    private String userId;
    private String memberRole;
    private Instant createdAt;
    private String membershipId;
    private String status;
    private Long permissionVersion;
    public String getMembershipId() { return membershipId; }
    public void setMembershipId(String value) { membershipId=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public Long getPermissionVersion() { return permissionVersion; }
    public void setPermissionVersion(Long value) { permissionVersion=value; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getMemberRole() { return memberRole; }
    public void setMemberRole(String memberRole) { this.memberRole = memberRole; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
