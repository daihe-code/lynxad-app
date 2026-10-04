package com.lynx.ad.modules.tenant.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_profile")
public class UserProfilePo {
    @TableId(type = IdType.INPUT)
    private String userId;
    private String nickname;
    private String email;
    private String avatarUrl;
    private String companyName;
    private String industry;
    private String position;
    private String monthlyBudget;
    private String adExperience;
    private String coreDemand;
    private String companyScale;
    private Instant completedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getMonthlyBudget() { return monthlyBudget; }
    public void setMonthlyBudget(String monthlyBudget) { this.monthlyBudget = monthlyBudget; }
    public String getAdExperience() { return adExperience; }
    public void setAdExperience(String adExperience) { this.adExperience = adExperience; }
    public String getCoreDemand() { return coreDemand; }
    public void setCoreDemand(String coreDemand) { this.coreDemand = coreDemand; }
    public String getCompanyScale() { return companyScale; }
    public void setCompanyScale(String companyScale) { this.companyScale = companyScale; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
