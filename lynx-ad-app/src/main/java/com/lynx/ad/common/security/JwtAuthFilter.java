package com.lynx.ad.common.security;

import com.lynx.ad.common.exception.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            RequestMeta.set(clientIp(request), request.getHeader("User-Agent"));
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    TenantPrincipal principal = jwtService.parse(header.substring(7).trim());
                    TenantContext.set(principal);
                } catch (BizException ignored) {
                    // expired/invalid JWT must not 500 public login
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            RequestMeta.clear();
        }
    }
}
