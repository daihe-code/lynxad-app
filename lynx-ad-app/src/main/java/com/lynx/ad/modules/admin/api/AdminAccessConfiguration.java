package com.lynx.ad.modules.admin.api;

import com.lynx.ad.modules.admin.application.AdminAuthService;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Every administrative endpoint is guarded, including endpoints added in the future. */
@Configuration
public class AdminAccessConfiguration implements WebMvcConfigurer {
    private final AdminAuthService auth;
    public AdminAccessConfiguration(AdminAuthService auth) { this.auth=auth; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) { auth.require();return true; }
        }).addPathPatterns("/api/v1/admin/**").excludePathPatterns("/api/v1/admin/auth/login");
    }
}
