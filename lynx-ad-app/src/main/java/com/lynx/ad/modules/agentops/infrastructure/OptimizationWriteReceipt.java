package com.lynx.ad.modules.agentops.infrastructure;

import com.lynx.ad.common.api.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** HTTP 200 alone is not a successful merchant write: business errors also use an envelope. */
@RestControllerAdvice
public class OptimizationWriteReceipt implements ResponseBodyAdvice<Object> {
    static final String SUCCESS=OptimizationWriteReceipt.class.getName()+".success";
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return true;}
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType contentType,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
        if(request instanceof ServletServerHttpRequest servlet)
            servlet.getServletRequest().setAttribute(SUCCESS,body instanceof ApiResponse<?> result&&result.code()==0);
        return body;
    }
}
