package com.imageaiplatform.ui.domain;

import java.util.Set;

public record UiScreen(String screenCode, String displayName, String routePath,
        Boolean publicAccess, Boolean permissionRequired, Boolean enabled) {
    public record Configuration(UiScreen screen, Set<String> permissionCodes) {}
}
