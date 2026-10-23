package com.lynx.ad.modules.tenant.domain.model;

import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.tenant.domain.service.PasswordRules;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class User {

    private final UserId id;
    private PhoneNumber phone;
    private String passwordHash;
    private final String originalPasswordHash;
    private UserProfile profile;
    private final Instant createdAt;

    public User(UserId id, PhoneNumber phone, String passwordHash, UserProfile profile) {
        this(id, phone, passwordHash, profile, Instant.now());
    }

    public User(UserId id, PhoneNumber phone, String passwordHash, UserProfile profile, Instant createdAt) {
        this.id = id;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.originalPasswordHash = passwordHash;
        this.profile = profile;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static User register(PhoneNumber phone) {
        return new User(new UserId(UUID.randomUUID().toString()), phone, null, null, Instant.now());
    }

    public void setPassword(String raw, PasswordHasher hasher) {
        PasswordRules.require(raw);
        this.passwordHash = hasher.hash(raw);
    }

    public void changePassword(String oldPassword, String raw, PasswordHasher hasher) {
        if (canPasswordLogin()) {
            if (oldPassword == null || !hasher.matches(oldPassword, passwordHash)) {
                throw new DomainException(1107, "原密码不正确");
            }
        }
        setPassword(raw, hasher);
    }

    public void changePhone(PhoneNumber next) {
        this.phone = next;
    }

    public boolean canPasswordLogin() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean matchesPassword(String raw, PasswordHasher hasher) {
        return canPasswordLogin() && hasher.matches(raw, passwordHash);
    }

    public void completeOnboarding(OnboardingCommand cmd) {
        if (profile == null) {
            profile = new UserProfile();
        }
        profile.complete(cmd, Instant.now());
    }

    public UserId id() {
        return id;
    }

    public PhoneNumber phone() {
        return phone;
    }

    public Optional<UserProfile> profile() {
        return Optional.ofNullable(profile);
    }

    public UserProfile profileOrCreate() {
        if (profile == null) {
            profile = new UserProfile();
        }
        return profile;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String originalPasswordHash() {
        return originalPasswordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
