package com.lynx.ad.modules.tenant.domain.model;

public interface PasswordHasher {

    String hash(String raw);

    boolean matches(String raw, String hashed);
}
