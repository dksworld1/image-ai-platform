package com.imageaiplatform.auth.sample;

import com.imageaiplatform.auth.service.AuthorizationService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** requirePermission을 @PreAuthorize의 boolean 표현식에 연결하는 샘플입니다. */
public class SamplePermissionGuard {
    private final AuthorizationService authorizationService;

    public SamplePermissionGuard(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public boolean require(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        authorizationService.requirePermission(authentication.getName(), permission);
        return true;
    }
}
