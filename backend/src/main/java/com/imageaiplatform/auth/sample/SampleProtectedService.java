package com.imageaiplatform.auth.sample;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;

public class SampleProtectedService {
    @PreAuthorize("@samplePermissionGuard.require('sample:item:read')")
    public String read() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
