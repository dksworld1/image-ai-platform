package com.imageaiplatform.auth.config;

import com.imageaiplatform.auth.controller.PublicApi;
import com.imageaiplatform.auth.controller.RequirePermission;
import com.imageaiplatform.auth.service.AuthorizationService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class ApiAuthorizationInterceptor implements HandlerInterceptor {
    private final AuthorizationService authorizationService;

    public ApiAuthorizationInterceptor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 서버 내부 오류 응답만 허용합니다. 직접 /error 요청은 아래 기본 거부 대상입니다.
        if (request.getDispatcherType() == DispatcherType.ERROR
                && request.getRequestURI().equals(request.getContextPath() + "/error")
                && request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) != null) {
            return true;
        }
        HandlerMethod method = handler instanceof HandlerMethod mapped ? mapped : null;
        boolean publicApi = method != null && method.hasMethodAnnotation(PublicApi.class);
        RequirePermission policy = method == null ? null : method.getMethodAnnotation(RequirePermission.class);
        if (publicApi && policy == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return reject(response, 401, "UNAUTHORIZED");
        }
        // 선언 누락·충돌은 요청 시 거부합니다. 기동 검사나 API 전수 스캔은 하지 않습니다.
        if (policy == null || publicApi) {
            return reject(response, 403, "FORBIDDEN");
        }
        try {
            if (policy.value().isEmpty()) {
                authorizationService.requireActiveAccount(authentication.getName());
            } else {
                authorizationService.requirePermission(authentication.getName(), policy.value());
            }
            return true;
        } catch (AuthenticationException exception) {
            return reject(response, 401, "UNAUTHORIZED");
        } catch (AccessDeniedException exception) {
            return reject(response, 403, "FORBIDDEN");
        } catch (DataAccessException exception) {
            return reject(response, 500, "INTERNAL_SERVER_ERROR");
        }
    }

    private boolean reject(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
        return false;
    }
}
