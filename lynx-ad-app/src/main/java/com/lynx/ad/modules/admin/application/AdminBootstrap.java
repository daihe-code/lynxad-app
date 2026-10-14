package com.lynx.ad.modules.admin.application;

import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Server-side initial provisioning only; no public registration or password reset endpoint. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AdminAccountStore accounts;private final PasswordEncoder passwords;
    @Value("${lynx.ad.admin.bootstrap-username:}") private String username;
    @Value("${lynx.ad.admin.bootstrap-password:}") private String password;
    public AdminBootstrap(AdminAccountStore accounts,PasswordEncoder passwords) { this.accounts=accounts;this.passwords=passwords; }
    @Override public void run(ApplicationArguments args) {
        if(username.isBlank()||password.isBlank()||accounts.count()>0)return;
        if(!username.matches("[a-zA-Z0-9_.@-]{3,64}"))throw new IllegalArgumentException("管理员登录名格式不正确");
        AdminAuthService.validatePassword(password);
        try { accounts.create(username,passwords.encode(password)); }
        catch(org.springframework.dao.DuplicateKeyException concurrentBootstrap) { if(accounts.username(username).isEmpty())throw concurrentBootstrap; }
    }
}
