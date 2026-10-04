package com.lynx.ad.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.JwtAuthFilter;
import com.lynx.ad.common.security.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthFilterTest {

    @Test
    void invalidBearerDoesNotAbortChain() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.parse(any())).thenThrow(new BizException(401, "未登录"));
        JwtAuthFilter filter = new JwtAuthFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenant/auth/password/login");
        request.addHeader("Authorization", "Bearer not-a-jwt");
        FilterChain chain = mock(FilterChain.class);
        assertDoesNotThrow(() -> filter.doFilter(request, new MockHttpServletResponse(), chain));
        verify(chain).doFilter(any(), any());
    }
}
