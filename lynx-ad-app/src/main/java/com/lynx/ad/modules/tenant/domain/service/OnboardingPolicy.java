package com.lynx.ad.modules.tenant.domain.service;

public class OnboardingPolicy {

    public boolean shouldCreateTenant(boolean alreadyMember) {
        return !alreadyMember;
    }
}
