package com.huzi.huaicodemother.config;

import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.util.Set;

/** 为新增的基于会话 Cookie 的写接口检查浏览器请求来源。 */
@Component
public class PersonalCenterOriginInterceptor implements HandlerInterceptor {

    private static final Set<String> PROTECTED_PATHS = Set.of("/user/update/my", "/user/quota/update");

    private final AllowedOrigins allowedOrigins;

    public PersonalCenterOriginInterceptor(AllowedOrigins allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !PROTECTED_PATHS.contains(path)) {
            return true;
        }
        String origin = request.getHeader("Origin");
        // Origin 存在（含空值、null 字符串、非法格式）时优先检查，绝不由 Referer 覆盖。
        boolean allowed = origin != null
                ? allowedOrigins.isAllowed(origin, false, request)
                : allowedOrigins.isAllowed(request.getHeader("Referer"), true, request);
        if (!allowed) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "请求来源不受信任");
        }
        return true;
    }
}
