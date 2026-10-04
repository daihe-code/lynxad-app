package com.lynx.ad.modules.tenant.domain.model;

import com.lynx.ad.common.domain.CompanyScale;
import com.lynx.ad.common.exception.DomainException;
import java.time.Instant;

public class UserProfile {

    private String nickname;
    private String email;
    private String avatarUrl;
    private String companyName;
    private String industry;
    private String position;
    private String monthlyBudget;
    private String adExperience;
    private String coreDemand;
    private CompanyScale companyScale = CompanyScale.empty();
    private Instant completedAt;

    public boolean isCompleted() {
        return completedAt != null;
    }

    public void requireAgreements(boolean terms, boolean privacy) {
        if (!terms || !privacy) {
            throw new DomainException(1001, "须同意服务协议与隐私政策");
        }
    }

    public void complete(OnboardingCommand cmd, Instant now) {
        requireAgreements(cmd.agreeTerms(), cmd.agreePrivacy());
        this.nickname = cmd.nickname();
        this.email = cmd.email();
        this.avatarUrl = cmd.avatarUrl();
        this.companyName = cmd.companyName();
        this.industry = cmd.industry();
        this.position = cmd.position();
        this.monthlyBudget = cmd.monthlyBudget();
        this.adExperience = cmd.adExperience();
        this.coreDemand = cmd.coreDemand();
        this.completedAt = now;
    }

    public void updateAccount(String nickname, String email, String companyName, String industry, CompanyScale scale) {
        this.nickname = nickname;
        this.email = email;
        this.companyName = companyName;
        this.industry = industry;
        this.companyScale = scale == null ? CompanyScale.empty() : scale;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public String nickname() {
        return nickname;
    }

    public String email() {
        return email;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public String companyName() {
        return companyName;
    }

    public String industry() {
        return industry;
    }

    public String position() {
        return position;
    }

    public String monthlyBudget() {
        return monthlyBudget;
    }

    public String adExperience() {
        return adExperience;
    }

    public String coreDemand() {
        return coreDemand;
    }

    public CompanyScale companyScale() {
        return companyScale == null ? CompanyScale.empty() : companyScale;
    }

    public static UserProfile restore(
            String nickname,
            String email,
            String avatarUrl,
            String companyName,
            String industry,
            String position,
            String monthlyBudget,
            String adExperience,
            String coreDemand,
            Instant completedAt) {
        return restore(
                nickname,
                email,
                avatarUrl,
                companyName,
                industry,
                position,
                monthlyBudget,
                adExperience,
                coreDemand,
                null,
                completedAt);
    }

    public static UserProfile restore(
            String nickname,
            String email,
            String avatarUrl,
            String companyName,
            String industry,
            String position,
            String monthlyBudget,
            String adExperience,
            String coreDemand,
            String companyScale,
            Instant completedAt) {
        UserProfile p = new UserProfile();
        p.nickname = nickname;
        p.email = email;
        p.avatarUrl = avatarUrl;
        p.companyName = companyName;
        p.industry = industry;
        p.position = position;
        p.monthlyBudget = monthlyBudget;
        p.adExperience = adExperience;
        p.coreDemand = coreDemand;
        p.companyScale = CompanyScale.parse(companyScale);
        p.completedAt = completedAt;
        return p;
    }
}
